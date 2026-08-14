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
    private final AiVariantQuestionService aiVariantQuestionService;

    public QuestionLearningAssetService(AiProvider aiProvider,
                                         AiConfig aiConfig,
                                         AiService aiService,
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
        this.aiService = aiService;
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
        aiService.checkDailyQuota(userId);

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
            aiService.logCall(userId, "asset_" + assetType.name().toLowerCase(), success, errorMessage, duration);
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
    public AiAssetFeedback getUserFeedback(Long questionId, String assetType, Long userId) {
        ensureAccessible(questionId, userId);
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
            case VISUAL_INTERACTIVE:
                return buildVisualInteractivePrompt(questionContext);
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
                + "请基于给定题目生成 **1 道可自动判分的单选变式题**。\n\n"
                + "要求：\n"
                + "1. 考查相同知识点，但换个角度或问法\n"
                + "2. 难度与原题相近\n"
                + "3. 提供 4 个不重复的选项，标签固定为 A、B、C、D，且只有一个正确答案\n"
                + "4. 解析必须说明正确选项为什么成立，并点出至少一个干扰项的误区\n"
                + "5. difficulty 为 1-5 的整数\n\n"
                + "你必须且只能输出一个合法 JSON 对象，不要输出 Markdown 代码块或其他文字：\n"
                + "{\n"
                + "  \"questionType\": \"SINGLE_CHOICE\",\n"
                + "  \"questionContent\": \"变式题题干\",\n"
                + "  \"options\": [\n"
                + "    {\"label\": \"A\", \"content\": \"选项A\"},\n"
                + "    {\"label\": \"B\", \"content\": \"选项B\"},\n"
                + "    {\"label\": \"C\", \"content\": \"选项C\"},\n"
                + "    {\"label\": \"D\", \"content\": \"选项D\"}\n"
                + "  ],\n"
                + "  \"correctAnswer\": \"A\",\n"
                + "  \"analysis\": \"提交后展示的简要解析\",\n"
                + "  \"difficulty\": 3\n"
                + "}";
        return new PromptPair(systemPrompt, "基于以下题目生成变式题：\n\n" + questionContext);
    }

    private PromptPair buildVisualInteractivePrompt(String questionContext) {
        String systemPrompt = "你是一位擅长可视化教学的计算机科学教育专家。你的任务是为编程/算法/数据结构/SQL 等过程类题目生成结构化的可视化讲解数据。\n\n"
                + "**你必须且只能输出一个合法的 JSON 对象，不要输出任何其他文本、Markdown 标记或解释性文字。**\n\n"
                + "JSON 格式如下：\n"
                + "```\n"
                + "{\n"
                + "  \"title\": \"简短的讲解标题\",\n"
                + "  \"summary\": \"一句话总结核心思路\",\n"
                + "  \"elements\": [\n"
                + "    {\n"
                + "      \"type\": \"text\",\n"
                + "      \"label\": \"模块标题\",\n"
                + "      \"content\": \"Markdown 文本内容\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"type\": \"step_list\",\n"
                + "      \"label\": \"解题步骤\",\n"
                + "      \"steps\": [\n"
                + "        {\"content\": \"步骤说明\", \"status\": \"done\", \"detail\": \"可选的详细说明\"}\n"
                + "      ]\n"
                + "    },\n"
                + "    {\n"
                + "      \"type\": \"table\",\n"
                + "      \"label\": \"变量变化过程\",\n"
                + "      \"headers\": [\"步骤\", \"变量i\", \"变量j\", \"说明\"],\n"
                + "      \"rows\": [\n"
                + "        [\"初始\", \"0\", \"5\", \"初始化\"],\n"
                + "        [\"第1步\", \"1\", \"4\", \"i加1, j减1\"]\n"
                + "      ]\n"
                + "    },\n"
                + "    {\n"
                + "      \"type\": \"state_array\",\n"
                + "      \"label\": \"数组状态变化\",\n"
                + "      \"description\": \"描述当前操作\",\n"
                + "      \"cells\": [\n"
                + "        {\"index\": 0, \"value\": \"3\", \"state\": \"visited\"},\n"
                + "        {\"index\": 1, \"value\": \"1\", \"state\": \"current\"},\n"
                + "        {\"index\": 2, \"value\": \"4\", \"state\": \"default\"}\n"
                + "      ]\n"
                + "    },\n"
                + "    {\n"
                + "      \"type\": \"matrix\",\n"
                + "      \"label\": \"DP 表格\",\n"
                + "      \"description\": \"动态规划填表过程\",\n"
                + "      \"headers\": [\"\", \"0\", \"1\", \"2\"],\n"
                + "      \"rows\": [\n"
                + "        [\"0\", {\"value\": \"0\", \"state\": \"visited\"}, {\"value\": \"0\", \"state\": \"visited\"}, {\"value\": \"0\", \"state\": \"visited\"}],\n"
                + "        [\"1\", {\"value\": \"0\", \"state\": \"visited\"}, {\"value\": \"1\", \"state\": \"current\"}, {\"value\": \"1\", \"state\": \"visited\"}],\n"
                + "        [\"2\", {\"value\": \"0\", \"state\": \"visited\"}, {\"value\": \"1\", \"state\": \"visited\"}, {\"value\": \"2\", \"state\": \"visited\"}]\n"
                + "      ]\n"
                + "    },\n"
                + "    {\n"
                + "      \"type\": \"tree\",\n"
                + "      \"label\": \"递归调用树\",\n"
                + "      \"root\": {\n"
                + "        \"name\": \"f(5)\",\n"
                + "        \"state\": \"current\",\n"
                + "        \"children\": [\n"
                + "          {\"name\": \"f(4)\", \"state\": \"visited\", \"children\": []},\n"
                + "          {\"name\": \"f(3)\", \"state\": \"visited\", \"children\": []}\n"
                + "        ]\n"
                + "      }\n"
                + "    },\n"
                + "    {\n"
                + "      \"type\": \"bar_chart\",\n"
                + "      \"label\": \"时间复杂度对比\",\n"
                + "      \"items\": [\n"
                + "        {\"label\": \"暴力法\", \"value\": 100},\n"
                + "        {\"label\": \"优化解\", \"value\": 10}\n"
                + "      ]\n"
                + "    },\n"
                + "    {\n"
                + "      \"type\": \"number_line\",\n"
                + "      \"label\": \"指针位置\",\n"
                + "      \"min\": 0,\n"
                + "      \"max\": 10,\n"
                + "      \"current\": 5,\n"
                + "      \"markers\": [\n"
                + "        {\"position\": 0, \"label\": \"left\"},\n"
                + "        {\"position\": 10, \"label\": \"right\"},\n"
                + "        {\"position\": 5, \"label\": \"mid\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n"
                + "```\n\n"
                + "**type 字段可选值**：\n"
                + "- `text`：普通文本讲解，字段为 label + content（Markdown）\n"
                + "- `step_list`：步骤列表，字段为 steps 数组，每个 step 有 content/status(done/current/pending)/detail\n"
                + "- `table`：数据表格，字段为 headers + rows（二维数组）\n"
                + "- `state_array`：数组/一维状态展示，字段为 description + cells 数组，每个 cell 有 index/value/state(default/current/highlight/visited/swapped/sorted)\n"
                + "- `matrix`：二维矩阵/DP 表格，字段为 headers + rows，每个 cell 可以是字符串或 {value, state} 对象\n"
                + "- `tree`：树结构，字段为 root 对象（name/state/children 递归），state 可选 default/current/visited\n"
                + "- `bar_chart`：柱状图对比，字段为 items 数组，每个 item 有 label/value（数值）\n"
                + "- `number_line`：数轴/指针位置，字段为 min/max/current + markers 数组（position/label）\n"
                + "- `mermaid`：Mermaid 流程图，字段为 code（Mermaid 语法）+ 可选 caption（图注说明），适合算法流程、SQL 执行顺序、网络协议交互、递归展开等\n"
                + "- `code_animation`：代码执行动画，字段为 language（可选，如 java/python/sql/c）+ code（完整代码字符串）+ steps 数组。每个 step 有：lineStart/lineEnd（当前高亮的代码行号，从1开始）、description（本步骤说明文字）、variables 数组（变量名/值/changed是否变化）、output（可选，本步骤产生的控制台输出）。适合展示算法逐步执行过程、代码调试模拟、循环变量跟踪等。\n"
                + "- `sql_execution`：SQL 执行顺序可视化，字段为 query（完整 SQL 语句）+ steps 数组（按 SQL 实际执行顺序排列）+ finalResult（可选，最终查询结果）。每个 step 有：clause（SQL 子句名，如 FROM、WHERE、GROUP BY 等）、description（本步骤执行说明）、resultHeaders（可选，中间结果列名）、resultRows（可选，中间结果数据行）、rowCount（可选，中间结果行数）。适合展示 SQL 查询的逻辑执行顺序、JOIN 过程、分组聚合等。**注意：steps 的顺序必须是 SQL 的逻辑执行顺序（FROM → JOIN → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT），而不是书写顺序。**\n"
                + "- `network_protocol`：网络协议交互过程可视化（时序图风格），字段为 entities（参与方名称数组）+ messages（消息数组，每条有 from/to 索引/content/可选 description/可选 state）。每条消息的 from 和 to 是 entities 数组的索引（从 0 开始），消息按时间顺序排列，渲染为从左到右的时序图。适合展示 TCP 三次握手/四次挥手、HTTP 请求响应、DNS 解析、ARP 协议、OSPF/BGP 路由交互等。\n"
                + "- `os_process`：操作系统过程可视化，字段为 steps（状态步骤数组，每步有 description + state 进程/线程状态数组）+ 可选 ganttChart（甘特图数组，每项有 label/start/end 时间点）。state 数组中每个 item 有 name/state/可选 info，state 值如 running/waiting/ready/blocked/terminated 等。适合展示进程调度算法（FCFS/SJF/RR/Priority）、页面置换算法（LRU/FIFO/OPT）、磁盘调度（SCAN/C-SCAN）、死锁检测、生产者-消费者等。\n\n"
                + "**重要规则**：\n"
                + "1. 只输出 JSON，不要输出 ```json ``` 代码块标记\n"
                + "2. text 类型可用于插入讲解性 Markdown 内容\n"
                + "3. 根据题型选择最合适的可视化元素组合（不必全部使用）\n"
                + "4. 对于循环/递归类题目，用 state_array 或 table 展示每步状态变化\n"
                + "5. 对于指针/区间类题目，用 number_line 展示位置\n"
                + "6. 对于 DP/矩阵类题目，用 matrix 展示填表过程\n"
                + "7. 对于树/图类题目，用 tree 展示结构\n"
                + "8. 对于算法流程、条件分支、循环逻辑，用 mermaid 展示流程图（flowchart TD 或 flowchart LR）\n"
                + "9. 对于 SQL 查询执行过程，优先使用 sql_execution 展示执行顺序（比 mermaid 更直观、有中间结果预览），只有在无法拆解执行步骤时才用 mermaid 作为备选\n"
                + "10. 对于递归展开过程，除了 tree 外也可用 mermaid flowchart 展示调用链\n"
                + "11. mermaid code 必须是合法的 Mermaid 语法，不要包含 ```mermaid 代码块标记，直接写 Mermaid 语法内容\n"
                + "12. 所有 state 字段值必须是以下之一：default, current, highlight, visited, swapped, sorted, done, pending\n"
                + "13. code_animation 的 steps 应覆盖代码的关键执行步骤（不必每行都展示，选择关键节点），variables 数组展示当前作用域内所有活跃变量的值，changed=true 标记本步骤发生变化的变量\n"
                + "14. code_animation 的 code 字段必须是完整可执行的代码（作为静态展示参考），lineStart/lineEnd 从 1 开始计数\n"
                + "15. sql_execution 的 steps 必须按照 SQL 逻辑执行顺序排列（典型顺序：FROM/JOIN → WHERE → GROUP BY → HAVING → SELECT → DISTINCT → ORDER BY → LIMIT/OFFSET），每个 step 应尽量提供 resultHeaders 和 resultRows 展示中间结果（至少 3-5 行示例数据），让读者能看到数据如何在每一步被过滤和变换\n"
                + "16. sql_execution 的 query 字段必须是原始 SQL 语句（方便对照），finalResult 展示最终输出结果\n"
                + "17. network_protocol 的 entities 必须按从左到右的排列顺序给出（如 [\"客户端\", \"服务器\"]），messages 必须按时间顺序排列，每条 message 的 from/to 是 entities 数组的索引（从 0 开始）。content 放消息名或数据包名，description 放该消息的详细说明\n"
                + "18. os_process 的 state 值建议使用以下术语：running（运行中）、ready（就绪）、waiting/blocked（等待/阻塞）、terminated（终止），info 字段可放额外信息如剩余时间片等。ganttChart 中的 start/end 是时间刻度（从 0 开始的整数），每项代表一个进程在某段时间的执行区间\n"
                + "19. 网络协议类题目（TCP/IP、HTTP、DNS、ARP 等）优先使用 network_protocol 元素\n"
                + "20. 操作系统类题目（进程调度、页面置换、磁盘调度、死锁等）优先使用 os_process 元素";
        return new PromptPair(systemPrompt, "请为以下题目生成可视化讲解数据（严格输出 JSON）：\n\n" + questionContext);
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

    private record PromptPair(String systemPrompt, String userPrompt) {}
}
