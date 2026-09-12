package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.AiResponse;
import com.learnplatform.dto.ReviewContextVO;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.dto.ReviewStatsVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
public class AiReviewSuggestionService {
    private static final String SYSTEM_PROMPT = "你是一位资深学习教练，精通间隔重复（Spaced Repetition）和艾宾浩斯遗忘曲线理论。"
            + "请根据用户当前的复习计划数据和答题表现，生成个性化复习建议。\n\n"
            + "要求：\n1. 分析用户的复习节奏和习惯\n2. 指出当前复习计划中的薄弱环节\n"
            + "3. 针对困难卡片给出具体的学习策略\n4. 建议每日复习量和时间安排\n"
            + "5. 给出保持或建立连续复习习惯的建议\n6. 如果有逾期卡片，给出优先级排序和追回策略\n"
            + "7. 语言亲切、实用、有激励性\n8. 使用 Markdown 格式输出，包含标题和列表";

    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;
    private final AiInvocationService invocationService;

    public AiReviewSuggestionService(
            WrongQuestionMapper wrongQuestionMapper,
            QuestionMapper questionMapper,
            CourseMapper courseMapper,
            AiInvocationService invocationService) {
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
        this.invocationService = invocationService;
    }

    public AiResponse generateReviewSuggestion(Long userId, Long courseId) {
        AiService.AiPrompt prompt = buildWrongQuestionPrompt(userId, courseId);
        return invocationService.call("review_suggestion", userId, prompt);
    }

    public void generateReviewSuggestionStream(Long userId, Long courseId, Consumer<String> onContent) {
        AiService.AiPrompt prompt = buildWrongQuestionPrompt(userId, courseId);
        invocationService.stream("review_suggestion_stream", userId, prompt, onContent);
    }

    public AiResponse generateReviewBasedSuggestion(Long userId) {
        AiService.AiPrompt prompt = noContextPrompt();
        return invocationService.call("review_based_suggestion", userId, prompt);
    }

    public void generateReviewBasedSuggestionStream(Long userId, Consumer<String> onContent) {
        invocationService.stream("review_based_suggestion_stream", userId, noContextPrompt(), onContent);
    }

    public AiService.AiPrompt buildReviewSuggestionPromptWithContext(ReviewContextVO context) {
        StringBuilder prompt = new StringBuilder("请根据以下复习数据给出个性化建议：\n\n");
        appendStats(prompt, context.getStats());
        appendDailyReviews(prompt, context.getRecentDailyReviews());
        appendDifficultCards(prompt, context.getDifficultCards());
        appendOverdueCards(prompt, context.getOverdueCards());
        return new AiService.AiPrompt(SYSTEM_PROMPT, prompt.toString());
    }

    public AiResponse generateReviewBasedSuggestionWithContext(Long userId, ReviewContextVO context) {
        return invocationService.call("review_based_suggestion", userId,
                buildReviewSuggestionPromptWithContext(context));
    }

    public void generateReviewBasedSuggestionStreamWithContext(
            Long userId, ReviewContextVO context, Consumer<String> onContent) {
        invocationService.stream("review_based_suggestion_stream", userId,
                buildReviewSuggestionPromptWithContext(context), onContent);
    }

    private AiService.AiPrompt noContextPrompt() {
        return new AiService.AiPrompt(SYSTEM_PROMPT,
                "请根据以下复习数据给出个性化建议（直接分析数据，不要复述数据）：\n\n用户暂无上下文数据");
    }

    private AiService.AiPrompt buildWrongQuestionPrompt(Long userId, Long courseId) {
        List<WrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(
                new LambdaQueryWrapper<WrongQuestion>()
                        .eq(WrongQuestion::getUserId, userId)
                        .eq(WrongQuestion::getDeleted, 0));
        StringBuilder context = new StringBuilder("用户错题数量：")
                .append(wrongQuestions.size()).append(" 道\n");
        if (!wrongQuestions.isEmpty()) {
            context.append("错题详情：\n");
            for (WrongQuestion wrongQuestion : wrongQuestions) {
                Question question = questionMapper.selectById(wrongQuestion.getQuestionId());
                if (question != null) {
                    context.append("- 题目ID:").append(question.getId())
                            .append("，题型:").append(question.getQuestionType())
                            .append("，答错次数:").append(wrongQuestion.getWrongCount())
                            .append("，掌握程度:").append(wrongQuestion.getMasteryLevel()).append("\n");
                }
            }
        }
        if (courseId != null) {
            Course course = courseMapper.selectById(courseId);
            if (course != null) {
                context.append("针对课程：").append(course.getName()).append("\n");
            }
        }
        String systemPrompt = "你是一位专业的学习顾问。请根据用户的错题情况，给出个性化的复习建议。"
                + "要求：\n1. 分析用户的薄弱环节\n2. 建议重点复习的知识点\n3. 推荐复习方法和计划\n"
                + "4. 给予鼓励和指导\n5. 使用 Markdown 格式输出";
        return new AiService.AiPrompt(systemPrompt, "请根据以下学习数据给出复习建议：\n\n" + context);
    }

    private void appendStats(StringBuilder prompt, ReviewStatsVO stats) {
        if (stats == null) {
            return;
        }
        prompt.append("## 复习统计\n")
                .append("- 总卡片数：").append(stats.getTotalCards()).append("\n")
                .append("- 今日待复习：").append(stats.getDueToday()).append("\n")
                .append("- 逾期未复习：").append(stats.getOverdue()).append("\n")
                .append("- 今日已完成：").append(stats.getReviewedToday()).append("\n")
                .append("- 新卡片：").append(stats.getNewCards()).append("\n")
                .append("- 学习中：").append(stats.getLearningCards()).append("\n")
                .append("- 已掌握：").append(stats.getMasteredCards()).append("\n")
                .append("- 困难卡片：").append(stats.getDifficultCards()).append("\n")
                .append("- 连续复习天数：").append(stats.getStreakDays()).append("\n")
                .append("- 平均简易因子：").append(String.format("%.2f", stats.getAvgEaseFactor())).append("\n\n");
    }

    private void appendDailyReviews(StringBuilder prompt, List<Integer> dailyReviews) {
        if (dailyReviews == null || dailyReviews.isEmpty()) {
            return;
        }
        String[] labels = {"6天前", "5天前", "4天前", "3天前", "2天前", "昨天", "今天"};
        prompt.append("## 近 7 天复习量\n");
        for (int i = 0; i < dailyReviews.size() && i < labels.length; i++) {
            prompt.append("- ").append(labels[i]).append("：").append(dailyReviews.get(i)).append(" 题\n");
        }
        prompt.append("\n");
    }

    private void appendDifficultCards(StringBuilder prompt, List<ReviewScheduleVO> cards) {
        if (cards == null || cards.isEmpty()) {
            return;
        }
        prompt.append("## 困难卡片（EF < 2.0）\n");
        for (ReviewScheduleVO card : cards) {
            prompt.append("- 「").append(questionLabel(card)).append("」 EF=")
                    .append(card.getEaseFactor() != null ? String.format("%.2f", card.getEaseFactor()) : "-")
                    .append("，间隔=").append(card.getIntervalDays()).append("天")
                    .append("，已复习=").append(card.getTotalReviews()).append("次")
                    .append("，课程=").append(courseLabel(card)).append("\n");
        }
        prompt.append("\n");
    }

    private void appendOverdueCards(StringBuilder prompt, List<ReviewScheduleVO> cards) {
        if (cards == null || cards.isEmpty()) {
            return;
        }
        prompt.append("## 逾期卡片\n");
        for (ReviewScheduleVO card : cards) {
            prompt.append("- 「").append(questionLabel(card)).append("」 逾期 ")
                    .append(card.getOverdueDays()).append(" 天")
                    .append("，课程=").append(courseLabel(card)).append("\n");
        }
        prompt.append("\n");
    }

    private String questionLabel(ReviewScheduleVO card) {
        return card.getQuestionContent() != null
                ? card.getQuestionContent() : "题目" + card.getQuestionId();
    }

    private String courseLabel(ReviewScheduleVO card) {
        return card.getCourseName() != null ? card.getCourseName() : "未知";
    }
}
