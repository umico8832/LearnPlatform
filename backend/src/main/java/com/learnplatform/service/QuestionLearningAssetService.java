package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import com.learnplatform.dto.AiAssetFeedbackVO;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.QuestionLearningAssetVO;
import com.learnplatform.entity.AiAssetFeedback;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionAiAsset;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.AiAssetFeedbackMapper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionAiAssetMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.ai.QuestionAssetPromptFactory;
import com.learnplatform.service.ai.QuestionAssetPromptFactory.Prompt;
import com.learnplatform.service.question.QuestionAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final AiCallGovernanceService callGovernanceService;
    private final QuestionAiAssetMapper questionAiAssetMapper;
    private final AiAssetFeedbackMapper aiAssetFeedbackMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final CourseMapper courseMapper;
    private final AiVariantQuestionService aiVariantQuestionService;

    public QuestionLearningAssetService(AiProvider aiProvider,
                                         AiConfig aiConfig,
                                         AiCallGovernanceService callGovernanceService,
                                         QuestionAiAssetMapper questionAiAssetMapper,
                                         AiAssetFeedbackMapper aiAssetFeedbackMapper,
                                         QuestionMapper questionMapper,
                                         QuestionOptionMapper questionOptionMapper,
                                         QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                         KnowledgePointMapper knowledgePointMapper,
                                         CourseMapper courseMapper,
                                         AiVariantQuestionService aiVariantQuestionService) {
        this.aiProvider = aiProvider;
        this.aiConfig = aiConfig;
        this.callGovernanceService = callGovernanceService;
        this.questionAiAssetMapper = questionAiAssetMapper;
        this.aiAssetFeedbackMapper = aiAssetFeedbackMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.courseMapper = courseMapper;
        this.aiVariantQuestionService = aiVariantQuestionService;
    }

    /**
     * 查询一道题的所有已缓存 AI 学习资产
     */
    public List<QuestionLearningAssetVO> getAssets(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (!QuestionAccessPolicy.isPublic(question)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }

        LambdaQueryWrapper<QuestionAiAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionAiAsset::getQuestionId, questionId);
        List<QuestionAiAsset> assets = questionAiAssetMapper.selectList(wrapper);

        return assets.stream().map(this::toVO).collect(Collectors.toList());
    }

    public List<QuestionLearningAssetVO> getAssets(Long questionId, Long userId) {
        ensureAccessible(questionId, userId);
        LambdaQueryWrapper<QuestionAiAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionAiAsset::getQuestionId, questionId);
        return questionAiAssetMapper.selectList(wrapper).stream().map(this::toVO).toList();
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
        ensureAccessible(questionId, userId);
        // 先查缓存
        QuestionAiAsset cached = findAsset(questionId, assetType.name());
        if (cached != null) {
            log.debug("命中 AI 学习资产缓存: questionId={}, type={}", questionId, assetType);
            return toVO(cached);
        }

        // 检查配额
        callGovernanceService.checkDailyQuota(userId);

        // 生成并记录调用日志
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        QuestionAiAsset asset;
        try {
            String content = generateAssetContent(questionId, assetType);
            asset = assetType == AiAssetType.VARIANT
                    ? aiVariantQuestionService.saveGeneratedAsset(questionId, aiConfig.getModel(), content)
                    : saveAsset(questionId, assetType, content);
            success = true;
        } catch (Exception e) {
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw e;
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            callGovernanceService.logCall(userId, "asset_" + assetType.name().toLowerCase(),
                    success, errorMessage, duration);
        }

        log.info("AI 学习资产已生成并缓存: questionId={}, type={}", questionId, assetType);

        return toVO(asset);
    }

    /**
     * 流式生成指定类型的 AI 学习资产（完成后自动缓存）
     */
    public void generateAssetStream(Long questionId, AiAssetType assetType, Long userId,
                                     Consumer<String> onContent) {
        ensureAccessible(questionId, userId);
        // 结构化变式题包含服务端私有答案，必须等完整 JSON 校验并脱敏后再返回。
        if (assetType == AiAssetType.VARIANT) {
            QuestionLearningAssetVO asset = generateOrGetAsset(questionId, assetType, userId);
            onContent.accept(asset.getContent());
            return;
        }

        // 先查缓存
        QuestionAiAsset cached = findAsset(questionId, assetType.name());
        if (cached != null) {
            // 缓存命中，直接流式返回
            onContent.accept(cached.getContent());
            return;
        }

        // 检查配额
        callGovernanceService.checkDailyQuota(userId);

        // 流式生成并收集完整内容，同时记录调用日志
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        StringBuilder fullContent = new StringBuilder();
        try {
            Prompt prompt = buildAssetPrompt(questionId, assetType);
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
            callGovernanceService.logCall(userId, "asset_" + assetType.name().toLowerCase() + "_stream", success,
                    errorMessage, duration);
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
        ensureAccessible(questionId, userId);
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
    public AiAssetFeedbackVO getUserFeedback(Long questionId, String assetType, Long userId) {
        ensureAccessible(questionId, userId);
        LambdaQueryWrapper<AiAssetFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAssetFeedback::getQuestionId, questionId)
               .eq(AiAssetFeedback::getAssetType, assetType)
               .eq(AiAssetFeedback::getUserId, userId);
        return AiAssetFeedbackVO.fromEntity(aiAssetFeedbackMapper.selectOne(wrapper));
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

    @Transactional
    public void clearAssets(Long questionId, Long userId) {
        ensureAccessible(questionId, userId);
        clearAssets(questionId);
    }

    // ======================== 内部方法 ========================

    private void ensureAccessible(Long questionId, Long userId) {
        Question question = questionMapper.selectById(questionId);
        if (!QuestionAccessPolicy.canAccess(question, userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
    }

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
        Prompt prompt = buildAssetPrompt(questionId, assetType);
        return aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt());
    }

    private Prompt buildAssetPrompt(Long questionId, AiAssetType assetType) {
        return QuestionAssetPromptFactory.build(assetType, buildQuestionContext(questionId));
    }

    /**
     * 构建题目上下文信息，包含题目、选项、知识点等
     */
    private String buildQuestionContext(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) { throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在"); }

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
            List<Long> kpIds = kps.stream().map(QuestionKnowledgePoint::getKnowledgePointId)
                    .collect(Collectors.toList());
            List<KnowledgePoint> kpList = knowledgePointMapper.selectBatchIds(kpIds);
            if (!kpList.isEmpty()) {
                sb.append("知识点：").append(kpList.stream().map(KnowledgePoint::getName)
                        .collect(Collectors.joining("、"))).append("\n");
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
        if (type == null) { return "未知"; }
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
        QuestionLearningAssetVO vo = new QuestionLearningAssetVO(
                asset.getId(),
                asset.getQuestionId(),
                asset.getAssetType(),
                label,
                asset.getContent(),
                asset.getModel(),
                asset.getCreateTime()
        );
        if (AiAssetType.VARIANT.name().equals(asset.getAssetType())) {
            vo.setVariantQuestion(aiVariantQuestionService.getPublicQuestion(asset.getId()));
        }
        return vo;
    }

}
