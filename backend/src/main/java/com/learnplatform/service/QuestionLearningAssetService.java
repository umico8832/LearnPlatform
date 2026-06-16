package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.QuestionLearningAssetVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import com.learnplatform.service.ai.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI 题目学习资产服务
 * 管理结构化 AI 学习资产的生成、缓存和查询
 */
@Service
public class QuestionLearningAssetService {

    private static final Logger log = LoggerFactory.getLogger(QuestionLearningAssetService.class);

    private final AiProvider aiProvider;
    private final AiConfig aiConfig;
    private final AiService aiService;
    private final QuestionAiAssetMapper questionAiAssetMapper;
    private final AiAssetFeedbackMapper aiAssetFeedbackMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final CourseMapper courseMapper;

    public QuestionLearningAssetService(AiProvider aiProvider,
                                         AiConfig aiConfig,
                                         AiService aiService,
                                         QuestionAiAssetMapper questionAiAssetMapper,
                                         AiAssetFeedbackMapper aiAssetFeedbackMapper,
                                         QuestionMapper questionMapper,
                                         QuestionOptionMapper questionOptionMapper,
                                         QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                         KnowledgePointMapper knowledgePointMapper,
                                         CourseMapper courseMapper) {
        this.aiProvider = aiProvider;
        this.aiConfig = aiConfig;
        this.aiService = aiService;
        this.questionAiAssetMapper = questionAiAssetMapper;
        this.aiAssetFeedbackMapper = aiAssetFeedbackMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 查询一道题的所有已缓存 AI 学习资产
     */
    public List<QuestionLearningAssetVO> getAssets(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");

        LambdaQueryWrapper<QuestionAiAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionAiAsset::getQuestionId, questionId);
        List<QuestionAiAsset> assets = questionAiAssetMapper.selectList(wrapper);

        return assets.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 查询一道题某种类型的已缓存资产
     */
    public QuestionLearningAssetVO getAsset(Long questionId, AiAssetType assetType) {
        QuestionAiAsset asset = findAsset(questionId, assetType.name());
        return asset != null ? toVO(asset) : null;
    }

    /**
     * 生成或获取指定类型的 AI 学习资产（同步，有缓存则直接返回）
     */
    public QuestionLearningAssetVO generateOrGetAsset(Long questionId, AiAssetType assetType, Long userId) {
        // 先查缓存
        QuestionAiAsset cached = findAsset(questionId, assetType.name());
        if (cached != null) {
            log.debug("命中 AI 学习资产缓存: questionId={}, type={}", questionId, assetType);
            return toVO(cached);
        }

        // 检查配额
        aiService.checkDailyQuota(userId);

        // 生成并记录调用日志
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        String content;
        try {
            content = generateAssetContent(questionId, assetType);
            success = true;
        } catch (Exception e) {
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw e;
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            aiService.logCall(userId, "asset_" + assetType.name().toLowerCase(), success, errorMessage, duration);
        }

        // 保存缓存
        QuestionAiAsset asset = saveAsset(questionId, assetType, content);
        log.info("AI 学习资产已生成并缓存: questionId={}, type={}", questionId, assetType);

        return toVO(asset);
    }

    /**
     * 流式生成指定类型的 AI 学习资产（完成后自动缓存）
     */
    public void generateAssetStream(Long questionId, AiAssetType assetType, Long userId,
                                     Consumer<String> onContent) {
        // 先查缓存
        QuestionAiAsset cached = findAsset(questionId, assetType.name());
        if (cached != null) {
            // 缓存命中，直接流式返回
            onContent.accept(cached.getContent());
            return;
        }

        // 检查配额
        aiService.checkDailyQuota(userId);

        // 流式生成并收集完整内容，同时记录调用日志
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        StringBuilder fullContent = new StringBuilder();
        try {
            PromptPair prompt = buildAssetPrompt(questionId, assetType);
            aiProvider.chatStream(prompt.systemPrompt(), prompt.userPrompt(), chunk -> {
                fullContent.append(chunk);
                onContent.accept(chunk);
            });
            success = true;
        } catch (Exception e) {
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw e;
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            aiService.logCall(userId, "asset_" + assetType.name().toLowerCase() + "_stream", success, errorMessage, duration);
        }

        // 生成完成后保存缓存
        try {
            saveAsset(questionId, assetType, fullContent.toString());
            log.info("AI 学习资产流式生成并缓存完成: questionId={}, type={}", questionId, assetType);
        } catch (Exception e) {
            log.warn("AI 学习资产缓存保存失败（不影响流式返回）: {}", e.getMessage());
        }
    }

    /**
     * 提交 AI 学习资产反馈（有帮助/无帮助）
     * 同一用户对同一题同一资产类型只能反馈一次，重复提交会更新
     */
    public void submitFeedback(Long questionId, String assetType, Long userId, Boolean helpful, String comment) {
        LambdaQueryWrapper<AiAssetFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAssetFeedback::getQuestionId, questionId)
               .eq(AiAssetFeedback::getAssetType, assetType)
               .eq(AiAssetFeedback::getUserId, userId);
        AiAssetFeedback existing = aiAssetFeedbackMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setHelpful(helpful);
            existing.setComment(comment);
            aiAssetFeedbackMapper.updateById(existing);
        } else {
            AiAssetFeedback feedback = new AiAssetFeedback();
            feedback.setQuestionId(questionId);
            feedback.setAssetType(assetType);
            feedback.setUserId(userId);
            feedback.setHelpful(helpful);
            feedback.setComment(comment);
            aiAssetFeedbackMapper.insert(feedback);
        }
        log.info("AI 资产反馈已提交: questionId={}, type={}, userId={}, helpful={}", questionId, assetType, userId, helpful);
    }

    /**
     * 查询当前用户对某题某类型资产的反馈
     */
    public AiAssetFeedback getUserFeedback(Long questionId, String assetType, Long userId) {
        LambdaQueryWrapper<AiAssetFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAssetFeedback::getQuestionId, questionId)
               .eq(AiAssetFeedback::getAssetType, assetType)
               .eq(AiAssetFeedback::getUserId, userId);
        return aiAssetFeedbackMapper.selectOne(wrapper);
    }

    /**
     * 删除某道题的所有缓存资产（可用于题目更新时清缓存）
     */
    @Transactional
    public void clearAssets(Long questionId) {
        LambdaQueryWrapper<QuestionAiAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionAiAsset::getQuestionId, questionId);
        List<QuestionAiAsset> assets = questionAiAssetMapper.selectList(wrapper);
        for (QuestionAiAsset asset : assets) {
            questionAiAssetMapper.deleteById(asset.getId());
        }
        log.info("已清除题目 {} 的所有 AI 学习资产缓存，共 {} 条", questionId, assets.size());
    }

    // ======================== 内部方法 ========================

    private QuestionAiAsset findAsset(Long questionId, String assetType) {
        LambdaQueryWrapper<QuestionAiAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionAiAsset::getQuestionId, questionId)
               .eq(QuestionAiAsset::getAssetType, assetType);
        return questionAiAssetMapper.selectOne(wrapper);
    }

    private QuestionAiAsset saveAsset(Long questionId, AiAssetType assetType, String content) {
        QuestionAiAsset asset = new QuestionAiAsset();
        asset.setQuestionId(questionId);
        asset.setAssetType(assetType.name());
        asset.setContent(content);
        asset.setModel(aiConfig.getModel());
        questionAiAssetMapper.insert(asset);
        return asset;
    }

    private String generateAssetContent(Long questionId, AiAssetType assetType) {
        PromptPair prompt = buildAssetPrompt(questionId, assetType);
        return aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt());
    }

    private PromptPair buildAssetPrompt(Long questionId, AiAssetType assetType) {
        String questionContext = buildQuestionContext(questionId);

        switch (assetType) {
            case FULL_EXPLANATION:
                return buildFullExplanationPrompt(questionContext);
            case BEGINNER_EXPLANATION:
                return buildBeginnerExplanationPrompt(questionContext);
            case STEP_BY_STEP:
                return buildStepByStepPrompt(questionContext);
            case WRONG_OPTION_ANALYSIS:
                return buildWrongOptionAnalysisPrompt(questionContext);
            case COMMON_MISTAKES:
                return buildCommonMistakesPrompt(questionContext);
            case VARIANT:
                return buildVariantPrompt(questionContext);
            default:
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "不支持的资产类型: " + assetType);
        }
    }

    private PromptPair buildFullExplanationPrompt(String questionContext) {
        String systemPrompt = "你是一位专业的教育辅导老师，擅长深度讲解题目。\n\n"
                + "请为以下题目生成一份**标准结构化解析**，包含以下部分：\n\n"
                + "## 📌 考查知识点\n"
                + "列出本题考查的核心知识点和概念。\n\n"
                + "## ✅ 正确答案分析\n"
                + "详细解释为什么正确答案是对的，推导或推理过程要清晰。\n\n"
                + "## ❌ 错误选项分析\n"
                + "逐一分析每个错误选项，解释为什么它是错的。\n\n"
                + "## 💡 关键思路\n"
                + "指出解题的关键切入点和核心思路。\n\n"
                + "## 📝 总结\n"
                + "一句话总结本题的核心考点和解题方法。\n\n"
                + "使用 Markdown 格式输出，语言专业但易懂。";
        return new PromptPair(systemPrompt, "请解析这道题目：\n\n" + questionContext);
    }

    private PromptPair buildBeginnerExplanationPrompt(String questionContext) {
        String systemPrompt = "你是一位耐心的辅导老师，擅长用最简单的方式给零基础的学生讲解题目。\n\n"
                + "请为以下题目生成一份**小白版解析**：\n\n"
                + "要求：\n"
                + "1. 假设读者完全没有相关知识背景\n"
                + "2. 少用术语，如果必须使用术语就立即解释\n"
                + "3. 多用类比、生活化的例子帮助理解\n"
                + "4. 一步一步引导，每步都解释为什么\n"
                + "5. 在关键知识点处多做铺垫\n"
                + "6. 最后做一个简短回顾，帮助记忆\n\n"
                + "使用 Markdown 格式输出。";
        return new PromptPair(systemPrompt, "请用最简单的方式解析这道题目：\n\n" + questionContext);
    }

    private PromptPair buildStepByStepPrompt(String questionContext) {
        String systemPrompt = "你是一位注重方法论的辅导老师。\n\n"
                + "请为以下题目生成一份**详细的步骤拆解**：\n\n"
                + "要求：\n"
                + "1. 将解题过程拆成明确的、可执行的步骤\n"
                + "2. 每个步骤标注序号（Step 1, Step 2...）\n"
                + "3. 每步说明「做什么」和「为什么这样做」\n"
                + "4. 标注每步的关键信息和注意事项\n"
                + "5. 如果有易错步骤，特别标注 ⚠️ 警告\n"
                + "6. 最后给出「步骤速查表」方便回顾\n\n"
                + "使用 Markdown 格式输出。";
        return new PromptPair(systemPrompt, "请拆解这道题目的解题步骤：\n\n" + questionContext);
    }

    private PromptPair buildWrongOptionAnalysisPrompt(String questionContext) {
        String systemPrompt = "你是一位精通出题心理学的教育专家。\n\n"
                + "请为以下题目生成一份**错误选项深度分析**：\n\n"
                + "要求：\n"
                + "1. 逐个分析每个错误选项\n"
                + "2. 解释该错误选项利用了什么常见误解或思维陷阱\n"
                + "3. 指出学生容易在什么地方「上当」\n"
                + "4. 给出「如何避免选错」的具体建议\n"
                + "5. 用 🎯 标记最具迷惑性的选项\n"
                + "6. 最后给出「防坑指南」速记要点\n\n"
                + "使用 Markdown 格式输出。";
        return new PromptPair(systemPrompt, "请分析这道题目的错误选项：\n\n" + questionContext);
    }

    private PromptPair buildCommonMistakesPrompt(String questionContext) {
        String systemPrompt = "你是一位经验丰富的出题老师和阅卷专家。\n\n"
                + "请为以下题目生成一份**常见误区分析**：\n\n"
                + "要求：\n"
                + "1. 列出学生在面对这类题目时最容易犯的 3-5 个错误\n"
                + "2. 每个误区说明：\n"
                + "   - 误区是什么\n"
                + "   - 为什么会犯这个错误\n"
                + "   - 正确的理解应该是什么\n"
                + "3. 用 🚫 标记每个误区\n"
                + "4. 最后给出「避坑口诀」或「做题检查清单」\n\n"
                + "使用 Markdown 格式输出。";
        return new PromptPair(systemPrompt, "请分析这道题目的常见误区：\n\n" + questionContext);
    }

    private PromptPair buildVariantPrompt(String questionContext) {
        String systemPrompt = "你是一位专业的出题老师。\n\n"
                + "请基于给定的题目，生成 **2 道变式题**：\n\n"
                + "要求：\n"
                + "1. 考查相同知识点，但换个角度或问法\n"
                + "2. 难度与原题相近\n"
                + "3. 每道变式题包含：题目、选项（如有）、正确答案、简要解析\n"
                + "4. 使用清晰的格式区分两道题\n\n"
                + "使用 Markdown 格式输出。";
        return new PromptPair(systemPrompt, "基于以下题目生成变式题：\n\n" + questionContext);
    }

    /**
     * 构建题目上下文信息，包含题目、选项、知识点等
     */
    private String buildQuestionContext(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");

        StringBuilder sb = new StringBuilder();
        sb.append("题型：").append(getTypeLabel(question.getQuestionType())).append("\n");
        sb.append("难度：").append(question.getDifficulty()).append("/5\n");
        sb.append("题目：").append(question.getContent()).append("\n");

        // 选项
        LambdaQueryWrapper<QuestionOption> optWrapper = new LambdaQueryWrapper<>();
        optWrapper.eq(QuestionOption::getQuestionId, question.getId()).orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOption> options = questionOptionMapper.selectList(optWrapper);
        if (!options.isEmpty()) {
            sb.append("选项：\n");
            for (QuestionOption opt : options) {
                sb.append("  ").append(opt.getOptionLabel()).append(". ").append(opt.getContent());
                if (opt.getIsCorrect() != null && opt.getIsCorrect() == 1) {
                    sb.append(" [正确答案]");
                }
                sb.append("\n");
            }
        }

        // 已有解析
        if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
            sb.append("原始解析：").append(question.getAnalysis()).append("\n");
        }

        // 知识点
        LambdaQueryWrapper<QuestionKnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
        kpWrapper.eq(QuestionKnowledgePoint::getQuestionId, question.getId());
        List<QuestionKnowledgePoint> kps = questionKnowledgePointMapper.selectList(kpWrapper);
        if (!kps.isEmpty()) {
            List<Long> kpIds = kps.stream().map(QuestionKnowledgePoint::getKnowledgePointId).collect(Collectors.toList());
            List<KnowledgePoint> kpList = knowledgePointMapper.selectBatchIds(kpIds);
            if (!kpList.isEmpty()) {
                sb.append("知识点：").append(kpList.stream().map(KnowledgePoint::getName).collect(Collectors.joining("、"))).append("\n");
            }
        }

        // 课程
        if (question.getCourseId() != null) {
            Course course = courseMapper.selectById(question.getCourseId());
            if (course != null) {
                sb.append("所属课程：").append(course.getName()).append("\n");
            }
        }

        return sb.toString();
    }

    private String getTypeLabel(String type) {
        if (type == null) return "未知";
        switch (type) {
            case "SINGLE_CHOICE": return "单选题";
            case "MULTIPLE_CHOICE": return "多选题";
            case "TRUE_FALSE": return "判断题";
            case "FILL_BLANK": return "填空题";
            case "SHORT_ANSWER": return "简答题";
            default: return type;
        }
    }

    private QuestionLearningAssetVO toVO(QuestionAiAsset asset) {
        String label = "";
        try {
            label = AiAssetType.valueOf(asset.getAssetType()).getLabel();
        } catch (IllegalArgumentException e) {
            label = asset.getAssetType();
        }
        return new QuestionLearningAssetVO(
                asset.getId(),
                asset.getQuestionId(),
                asset.getAssetType(),
                label,
                asset.getContent(),
                asset.getModel(),
                asset.getCreateTime()
        );
    }

    private record PromptPair(String systemPrompt, String userPrompt) {}
}