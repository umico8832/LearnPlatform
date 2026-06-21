package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import com.learnplatform.dto.AiResponse;
import com.learnplatform.dto.ReviewContextVO;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.dto.ReviewStatsVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.ai.AiCostCalculator;
import com.learnplatform.service.ai.AiTokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI 业务服务
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiProvider aiProvider;
    private final AiCostCalculator aiCostCalculator;
    private final AiConfig aiConfig;
    private final AiCallLogMapper aiCallLogMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final CourseMapper courseMapper;
    private final WrongQuestionMapper wrongQuestionMapper;

    public AiService(AiProvider aiProvider,
                     AiCostCalculator aiCostCalculator,
                     AiConfig aiConfig,
                     AiCallLogMapper aiCallLogMapper,
                     QuestionMapper questionMapper,
                     QuestionOptionMapper questionOptionMapper,
                     QuestionKnowledgePointMapper questionKnowledgePointMapper,
                     KnowledgePointMapper knowledgePointMapper,
                     CourseMapper courseMapper,
                     WrongQuestionMapper wrongQuestionMapper) {
        this.aiProvider = aiProvider;
        this.aiCostCalculator = aiCostCalculator;
        this.aiConfig = aiConfig;
        this.aiCallLogMapper = aiCallLogMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.courseMapper = courseMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
    }

    /**
     * AI 生成题目解析
     */
    public AiResponse generateExplanation(Long questionId) {
        AiPrompt prompt = buildExplanationPrompt(questionId);
        String content = aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt());
        return new AiResponse(content, "ai");
    }

    public AiResponse generateExplanation(Long questionId, Long userId) {
        AiPrompt prompt = buildExplanationPrompt(questionId);
        return callWithLog("explanation", userId, () -> aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt()));
    }

    public void generateExplanationStream(Long questionId, Consumer<String> onContent) {
        AiPrompt prompt = buildExplanationPrompt(questionId);
        aiProvider.chatStream(prompt.systemPrompt(), prompt.userPrompt(), onContent);
    }

    public void generateExplanationStream(Long questionId, Long userId, Consumer<String> onContent) {
        AiPrompt prompt = buildExplanationPrompt(questionId);
        callStreamWithLog("explanation_stream", userId, () -> aiProvider.chatStream(prompt.systemPrompt(), prompt.userPrompt(), onContent));
    }

    /**
     * AI 生成变式题
     */
    public AiResponse generateVariant(Long questionId) {
        AiPrompt prompt = buildVariantPrompt(questionId);
        String content = aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt());
        return new AiResponse(content, "ai");
    }

    public AiResponse generateVariant(Long questionId, Long userId) {
        AiPrompt prompt = buildVariantPrompt(questionId);
        return callWithLog("variant", userId, () -> aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt()));
    }

    public void generateVariantStream(Long questionId, Consumer<String> onContent) {
        AiPrompt prompt = buildVariantPrompt(questionId);
        aiProvider.chatStream(prompt.systemPrompt(), prompt.userPrompt(), onContent);
    }

    public void generateVariantStream(Long questionId, Long userId, Consumer<String> onContent) {
        AiPrompt prompt = buildVariantPrompt(questionId);
        callStreamWithLog("variant_stream", userId, () -> aiProvider.chatStream(prompt.systemPrompt(), prompt.userPrompt(), onContent));
    }

    private AiPrompt buildExplanationPrompt(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");

        String questionContext = buildQuestionContext(question);

        String systemPrompt = "你是一位专业的教育辅导老师。请为以下题目提供详细、清晰的解析。"
                + "要求：\n1. 解释题目考查的知识点\n2. 分析正确答案的原因\n3. 如果有错误选项，解释为什么是错误的\n"
                + "4. 用简洁易懂的语言\n5. 使用 Markdown 格式输出";
        return new AiPrompt(systemPrompt, "请解析这道题目：\n\n" + questionContext);
    }

    private AiPrompt buildVariantPrompt(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");

        String questionContext = buildQuestionContext(question);

        String systemPrompt = "你是一位专业的出题老师。请基于给定的题目，生成 1-2 道变式题（类似知识点但不同问法）。"
                + "要求：\n1. 考查相同知识点但换个角度\n2. 难度与原题相近\n3. 包含题目、选项和正确答案\n"
                + "4. 使用 Markdown 格式输出";
        return new AiPrompt(systemPrompt, "基于以下题目生成变式题：\n\n" + questionContext);
    }

    /**
     * AI 生成复习建议
     */
    public AiResponse generateReviewSuggestion(Long userId, Long courseId) {
        AiPrompt prompt = buildReviewSuggestionPrompt(userId, courseId);
        return callWithLog("review_suggestion", userId, () -> aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt()));
    }

    public void generateReviewSuggestionStream(Long userId, Long courseId, Consumer<String> onContent) {
        AiPrompt prompt = buildReviewSuggestionPrompt(userId, courseId);
        callStreamWithLog("review_suggestion_stream", userId, () -> aiProvider.chatStream(prompt.systemPrompt(), prompt.userPrompt(), onContent));
    }

    private AiPrompt buildReviewSuggestionPrompt(Long userId, Long courseId) {
        LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
        wqWrapper.eq(WrongQuestion::getUserId, userId).eq(WrongQuestion::getDeleted, 0);
        List<WrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(wqWrapper);

        StringBuilder userContext = new StringBuilder();
        userContext.append("用户错题数量：").append(wrongQuestions.size()).append(" 道\n");

        if (!wrongQuestions.isEmpty()) {
            userContext.append("错题详情：\n");
            for (WrongQuestion wq : wrongQuestions) {
                Question q = questionMapper.selectById(wq.getQuestionId());
                if (q != null) {
                    userContext.append("- 题目ID:").append(q.getId())
                            .append("，题型:").append(q.getQuestionType())
                            .append("，答错次数:").append(wq.getWrongCount())
                            .append("，掌握程度:").append(wq.getMasteryLevel()).append("\n");
                }
            }
        }

        if (courseId != null) {
            Course course = courseMapper.selectById(courseId);
            if (course != null) {
                userContext.append("针对课程：").append(course.getName()).append("\n");
            }
        }

        String systemPrompt = "你是一位专业的学习顾问。请根据用户的错题情况，给出个性化的复习建议。"
                + "要求：\n1. 分析用户的薄弱环节\n2. 建议重点复习的知识点\n3. 推荐复习方法和计划\n"
                + "4. 给予鼓励和指导\n5. 使用 Markdown 格式输出";

        return new AiPrompt(systemPrompt, "请根据以下学习数据给出复习建议：\n\n" + userContext);
    }

    /**
     * AI 生成知识点总结
     */
    public AiResponse generateSummary(Long knowledgePointId) {
        return generateSummary(knowledgePointId, null);
    }

    public AiResponse generateSummary(Long knowledgePointId, Long userId) {
        KnowledgePoint kp = knowledgePointMapper.selectById(knowledgePointId);
        if (kp == null) throw new BusinessException(ResultCode.NOT_FOUND, "知识点不存在");

        String courseName = "";
        if (kp.getCourseId() != null) {
            Course course = courseMapper.selectById(kp.getCourseId());
            if (course != null) courseName = course.getName();
        }

        String systemPrompt = "你是一位专业的教育内容创作者。请为以下知识点生成一份简洁的知识总结。"
                + "要求：\n1. 清晰解释知识点的定义和概念\n2. 列出核心要点\n3. 提供实际例子\n"
                + "4. 如果有相关公式或规则请列出\n5. 使用 Markdown 格式输出";

        String userPrompt = String.format("请总结以下知识点：\n课程：%s\n知识点：%s", courseName, kp.getName());
        return callWithLog("summary", userId, () -> aiProvider.chat(systemPrompt, userPrompt));
    }

    // ======================== 配额检查 ========================

    /**
     * 检查用户每日 AI 调用配额，超限则抛出异常
     */
    public void checkDailyQuota(Long userId) {
        int dailyQuota = aiConfig.getDailyQuota();
        if (dailyQuota <= 0) return; // 不限制

        Long todayCount = countTodayCalls(userId);
        if (todayCount >= dailyQuota) {
            throw new BusinessException(ResultCode.QUOTA_EXCEEDED,
                    "今日 AI 调用次数已达上限（" + dailyQuota + " 次），请明天再试");
        }
        log.debug("用户 {} 今日已调用 AI {} 次，配额 {} 次", userId, todayCount, dailyQuota);
    }

    /**
     * 查询用户今日 AI 调用次数
     */
    public long countTodayCalls(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiCallLog::getUserId, userId)
               .ge(AiCallLog::getCreateTime, todayStart);
        Long count = aiCallLogMapper.selectCount(wrapper);
        return count != null ? count : 0;
    }

    /**
     * 获取用户今日 AI 调用用量信息（用于接口返回）
     * @return int[] {todayCount, dailyQuota}
     */
    public int[] getDailyUsage(Long userId) {
        int dailyQuota = aiConfig.getDailyQuota();
        long todayCount = countTodayCalls(userId);
        return new int[]{(int) todayCount, dailyQuota};
    }

    // ======================== 日志工具方法 ========================

    /**
     * 公开的 AI 调用日志记录方法（供其他 Service 调用）
     */
    public void logCall(Long userId, String functionType, boolean success, String errorMessage, int duration) {
        saveLog(userId, functionType, success, errorMessage, duration);
    }

    /**
     * 带日志记录的同步 AI 调用
     */
    private AiResponse callWithLog(String functionType, Long userId, AiCallable callable) {
        checkDailyQuota(userId);
        long start = System.currentTimeMillis();
        boolean success = false;
        String content = null;
        String errorMessage = null;
        try {
            content = callable.call();
            success = true;
            return new AiResponse(content, "ai");
        } catch (Exception e) {
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw e;
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            saveLog(userId, functionType, success, errorMessage, duration);
        }
    }

    /**
     * 带日志记录的流式 AI 调用
     */
    private void callStreamWithLog(String functionType, Long userId, Runnable runnable) {
        checkDailyQuota(userId);
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        try {
            runnable.run();
            success = true;
        } catch (Exception e) {
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw e;
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            saveLog(userId, functionType, success, errorMessage, duration);
        }
    }

    private void saveLog(Long userId, String functionType, boolean success, String errorMessage, int duration) {
        try {
            AiCallLog callLog = new AiCallLog();
            callLog.setUserId(userId);
            callLog.setFunctionType(functionType);
            callLog.setModel(aiConfig.getModel());
            callLog.setStatus(success ? 1 : 0);
            callLog.setErrorMessage(errorMessage);
            callLog.setDuration(duration);
            AiTokenUsage tokenUsage = success ? aiProvider.getLastTokenUsage() : null;
            if (tokenUsage != null) {
                callLog.setTokensUsed(tokenUsage.totalTokens());
                callLog.setPromptTokens(tokenUsage.promptTokens());
                callLog.setCompletionTokens(tokenUsage.completionTokens());
                callLog.setCostUsd(aiCostCalculator.calculate(callLog.getModel(), tokenUsage));
            }
            aiCallLogMapper.insert(callLog);
            log.info("AI 调用日志已记录: type={}, userId={}, success={}, duration={}ms, tokens={}, costUsd={}",
                    functionType, userId, success, duration, callLog.getTokensUsed(), callLog.getCostUsd());
        } catch (Exception e) {
            // 日志记录失败不应影响主流程
            log.warn("AI 调用日志记录失败: {}", e.getMessage());
        }
    }

    // ======================== 私有方法 ========================

    private String buildQuestionContext(Question question) {
        StringBuilder sb = new StringBuilder();
        sb.append("题型：").append(getTypeLabel(question.getQuestionType())).append("\n");
        sb.append("题目：").append(question.getContent()).append("\n");

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

        if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
            sb.append("解析：").append(question.getAnalysis()).append("\n");
        }

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

    /**
     * AI 基于间隔重复复习数据生成个性化复习建议（同步）
     */
    public AiResponse generateReviewBasedSuggestion(Long userId) {
        AiPrompt prompt = buildReviewBasedSuggestionPrompt(userId);
        return callWithLog("review_based_suggestion", userId,
                () -> aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt()));
    }

    /**
     * AI 基于间隔重复复习数据生成个性化复习建议（流式 SSE）
     */
    public void generateReviewBasedSuggestionStream(Long userId, Consumer<String> onContent) {
        AiPrompt prompt = buildReviewBasedSuggestionPrompt(userId);
        callStreamWithLog("review_based_suggestion_stream", userId,
                () -> aiProvider.chatStream(prompt.systemPrompt(), prompt.userPrompt(), onContent));
    }

    private AiPrompt buildReviewBasedSuggestionPrompt(Long userId) {
        ReviewContextVO ctx = new ReviewContextVO();
        // 这里需要从 SpacedRepetitionService 获取上下文，但为了避免循环依赖，我们直接在这里构建
        // 由调用方传入 context 或在 controller 层组装

        String systemPrompt = "你是一位资深学习教练，精通间隔重复（Spaced Repetition）和艾宾浩斯遗忘曲线理论。"
                + "请根据用户当前的复习计划数据和答题表现，生成个性化复习建议。\n\n"
                + "要求：\n"
                + "1. 分析用户的复习节奏和习惯\n"
                + "2. 指出当前复习计划中的薄弱环节\n"
                + "3. 针对困难卡片给出具体的学习策略\n"
                + "4. 建议每日复习量和时间安排\n"
                + "5. 给出保持或建立连续复习习惯的建议\n"
                + "6. 如果有逾期卡片，给出优先级排序和追回策略\n"
                + "7. 语言亲切、实用、有激励性\n"
                + "8. 使用 Markdown 格式输出，包含标题和列表";

        return new AiPrompt(systemPrompt, "请根据以下复习数据给出个性化建议（直接分析数据，不要复述数据）：\n\n"
                + "用户暂无上下文数据");
    }

    /**
     * 构建基于完整复习上下文的 AI 建议 Prompt（由 ReviewController 调用）
     */
    public AiPrompt buildReviewSuggestionPromptWithContext(ReviewContextVO ctx) {
        String systemPrompt = "你是一位资深学习教练，精通间隔重复（Spaced Repetition）和艾宾浩斯遗忘曲线理论。"
                + "请根据用户当前的复习计划数据和答题表现，生成个性化复习建议。\n\n"
                + "要求：\n"
                + "1. 分析用户的复习节奏和习惯\n"
                + "2. 指出当前复习计划中的薄弱环节\n"
                + "3. 针对困难卡片给出具体的学习策略\n"
                + "4. 建议每日复习量和时间安排\n"
                + "5. 给出保持或建立连续复习习惯的建议\n"
                + "6. 如果有逾期卡片，给出优先级排序和追回策略\n"
                + "7. 语言亲切、实用、有激励性\n"
                + "8. 使用 Markdown 格式输出，包含标题和列表";

        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下复习数据给出个性化建议：\n\n");

        // 统计概览
        ReviewStatsVO stats = ctx.getStats();
        if (stats != null) {
            sb.append("## 复习统计\n");
            sb.append("- 总卡片数：").append(stats.getTotalCards()).append("\n");
            sb.append("- 今日待复习：").append(stats.getDueToday()).append("\n");
            sb.append("- 逾期未复习：").append(stats.getOverdue()).append("\n");
            sb.append("- 今日已完成：").append(stats.getReviewedToday()).append("\n");
            sb.append("- 新卡片：").append(stats.getNewCards()).append("\n");
            sb.append("- 学习中：").append(stats.getLearningCards()).append("\n");
            sb.append("- 已掌握：").append(stats.getMasteredCards()).append("\n");
            sb.append("- 困难卡片：").append(stats.getDifficultCards()).append("\n");
            sb.append("- 连续复习天数：").append(stats.getStreakDays()).append("\n");
            sb.append("- 平均简易因子：").append(String.format("%.2f", stats.getAvgEaseFactor())).append("\n\n");
        }

        // 近 7 天复习量
        if (ctx.getRecentDailyReviews() != null && !ctx.getRecentDailyReviews().isEmpty()) {
            sb.append("## 近 7 天复习量\n");
            java.util.List<Integer> daily = ctx.getRecentDailyReviews();
            String[] dayLabels = {"6天前", "5天前", "4天前", "3天前", "2天前", "昨天", "今天"};
            for (int i = 0; i < daily.size() && i < 7; i++) {
                sb.append("- ").append(dayLabels[i]).append("：").append(daily.get(i)).append(" 题\n");
            }
            sb.append("\n");
        }

        // 困难卡片
        if (ctx.getDifficultCards() != null && !ctx.getDifficultCards().isEmpty()) {
            sb.append("## 困难卡片（EF < 2.0）\n");
            for (ReviewScheduleVO card : ctx.getDifficultCards()) {
                sb.append("- 「").append(card.getQuestionContent() != null ? card.getQuestionContent() : "题目" + card.getQuestionId())
                  .append("」 EF=").append(card.getEaseFactor() != null ? String.format("%.2f", card.getEaseFactor()) : "-")
                  .append("，间隔=").append(card.getIntervalDays()).append("天")
                  .append("，已复习=").append(card.getTotalReviews()).append("次")
                  .append("，课程=").append(card.getCourseName() != null ? card.getCourseName() : "未知")
                  .append("\n");
            }
            sb.append("\n");
        }

        // 逾期卡片
        if (ctx.getOverdueCards() != null && !ctx.getOverdueCards().isEmpty()) {
            sb.append("## 逾期卡片\n");
            for (ReviewScheduleVO card : ctx.getOverdueCards()) {
                sb.append("- 「").append(card.getQuestionContent() != null ? card.getQuestionContent() : "题目" + card.getQuestionId())
                  .append("」 逾期 ").append(card.getOverdueDays()).append(" 天")
                  .append("，课程=").append(card.getCourseName() != null ? card.getCourseName() : "未知")
                  .append("\n");
            }
            sb.append("\n");
        }

        return new AiPrompt(systemPrompt, sb.toString());
    }

    /**
     * 基于完整复习上下文生成 AI 建议（同步，供 ReviewController 调用）
     */
    public AiResponse generateReviewBasedSuggestionWithContext(Long userId, ReviewContextVO ctx) {
        AiPrompt prompt = buildReviewSuggestionPromptWithContext(ctx);
        return callWithLog("review_based_suggestion", userId,
                () -> aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt()));
    }

    /**
     * 基于完整复习上下文生成 AI 建议（流式 SSE，供 ReviewController 调用）
     */
    public void generateReviewBasedSuggestionStreamWithContext(Long userId, ReviewContextVO ctx, Consumer<String> onContent) {
        AiPrompt prompt = buildReviewSuggestionPromptWithContext(ctx);
        callStreamWithLog("review_based_suggestion_stream", userId,
                () -> aiProvider.chatStream(prompt.systemPrompt(), prompt.userPrompt(), onContent));
    }

    record AiPrompt(String systemPrompt, String userPrompt) {}

    @FunctionalInterface
    private interface AiCallable {
        String call();
    }
}
