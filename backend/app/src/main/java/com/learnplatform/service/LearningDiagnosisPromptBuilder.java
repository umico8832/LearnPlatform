package com.learnplatform.service;

import com.learnplatform.dto.LearningDiagnosisVO;
import org.springframework.stereotype.Component;

/**
 * 学习诊断的 AI 提示词构建器。
 *
 * <p>只负责根据诊断结果拼接纯文本提示词，不访问任何数据源，也不发起 AI 调用；
 * 便于独立测试提示词内容，并避免诊断主服务继续膨胀。</p>
 */
@Component
public class LearningDiagnosisPromptBuilder {

    /** 构建 AI 学习顾问的系统提示词。 */
    public String systemPrompt() {
        return "你是一位专业的 AI 学习顾问，擅长根据学生的学习数据给出个性化、可操作的学习建议。\n\n"
                + "要求：\n"
                + "1. 根据用户的具体学习数据（薄弱知识点、错题模式、学习习惯等）给出有针对性的建议\n"
                + "2. 建议要具体可操作，不要泛泛而谈\n"
                + "3. 分析用户的学习优势和不足，给出平衡的评价\n"
                + "4. 为用户制定短期（本周）和中期（本月）学习计划\n"
                + "5. 给予鼓励，但不要过度夸奖\n"
                + "6. 使用 Markdown 格式输出，包含标题、列表等结构化内容\n"
                + "7. 回复长度控制在 500-800 字，不要太长\n"
                + "8. 使用中文回复";
    }

    /** 根据诊断结果构建用户提示词。 */
    public String userPrompt(LearningDiagnosisVO diagnosis) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下学习数据，为该用户生成个性化的 AI 学习建议：\n\n");

        // 基本数据
        sb.append("## 基本学习数据\n");
        sb.append("- 总刷题数：").append(diagnosis.getTotalPractice()).append(" 道\n");
        sb.append("- 总正确率：").append(String.format("%.1f%%", diagnosis.getOverallCorrectRate())).append("\n");
        sb.append("- 连续学习天数：").append(diagnosis.getStreakDays()).append(" 天\n");
        sb.append("- 近 30 天活跃天数：").append(diagnosis.getActiveDaysLast30()).append(" 天\n\n");

        // 薄弱知识点
        if (diagnosis.getWeakPoints() != null && !diagnosis.getWeakPoints().isEmpty()) {
            sb.append("## 薄弱知识点（按优先级排序）\n");
            for (LearningDiagnosisVO.WeakPoint wp : diagnosis.getWeakPoints()) {
                sb.append("- ").append(wp.getKnowledgePointName())
                        .append("（").append(wp.getCourseName()).append("）：正确率 ")
                        .append(String.format("%.1f%%", wp.getCorrectRate()))
                        .append("，状态=").append(wp.getMasteryStatus())
                        .append("，练习 ").append(wp.getTotalAttempts()).append(" 次")
                        .append("，错 ").append(wp.getWrongCount()).append(" 题\n");
            }
            sb.append("\n");
        }

        // 课程掌握概况
        if (diagnosis.getCourseMasteries() != null && !diagnosis.getCourseMasteries().isEmpty()) {
            sb.append("## 课程掌握概况\n");
            for (LearningDiagnosisVO.CourseMastery cm : diagnosis.getCourseMasteries()) {
                sb.append("- ").append(cm.getCourseName())
                        .append("：正确率 ").append(String.format("%.1f%%", cm.getCorrectRate()))
                        .append("，练习 ").append(cm.getTotalAttempts()).append(" 次")
                        .append("，薄弱知识点 ").append(cm.getWeakPointCount()).append(" 个\n");
            }
            sb.append("\n");
        }

        // 错因分析
        if (diagnosis.getErrorPatterns() != null) {
            LearningDiagnosisVO.ErrorPatternSummary ep = diagnosis.getErrorPatterns();
            sb.append("## 错因分析\n");
            sb.append("- 反复出错题目数：").append(ep.getRepeatedErrorCount()).append(" 道\n");
            sb.append("- 近 7 天新增错题：").append(ep.getRecentNewWrongCount()).append(" 道\n");
            if (ep.getMasteryDistribution() != null) {
                sb.append("- 错题掌握程度分布：");
                ep.getMasteryDistribution().forEach((k, v) -> sb.append(k).append("=").append(v).append(" "));
                sb.append("\n");
            }
            if (ep.getTopErrorCourses() != null && !ep.getTopErrorCourses().isEmpty()) {
                sb.append("- 高频错题课程：");
                ep.getTopErrorCourses().forEach(c -> sb.append(c.getCourseName()).append("(")
                        .append(c.getWrongCount()).append(") "));
                sb.append("\n");
            }
            if (ep.getQuestionTypeDistribution() != null && !ep.getQuestionTypeDistribution().isEmpty()) {
                sb.append("- 错题题型分布：");
                ep.getQuestionTypeDistribution().forEach((k, v) -> sb.append(k).append("=").append(v).append(" "));
                sb.append("\n");
            }
            if (ep.getDifficultyDistribution() != null && !ep.getDifficultyDistribution().isEmpty()) {
                sb.append("- 错题难度分布：");
                ep.getDifficultyDistribution().forEach((k, v) -> sb.append(k).append("星=").append(v).append(" "));
                sb.append("\n");
            }
            if (ep.getKnowledgePointErrors() != null && !ep.getKnowledgePointErrors().isEmpty()) {
                sb.append("- 知识点错因排名：\n");
                ep.getKnowledgePointErrors().forEach(r -> sb.append("  · ").append(r.getKnowledgePointName())
                        .append("（").append(r.getCourseName()).append("）：错 ").append(r.getWrongCount())
                        .append(" 题，正确率 ").append(String.format("%.1f%%", r.getCorrectRate())).append("\n"));
            }
            if (ep.getWeeklyErrorTrend() != null && !ep.getWeeklyErrorTrend().isEmpty()) {
                sb.append("- 近 4 周错题趋势：");
                ep.getWeeklyErrorTrend().forEach(w -> sb.append(w.get("label")).append("=")
                        .append(w.get("count")).append(" "));
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 学习习惯
        if (diagnosis.getLearningHabit() != null) {
            LearningDiagnosisVO.LearningHabit habit = diagnosis.getLearningHabit();
            sb.append("## 学习习惯\n");
            sb.append("- 日均刷题：").append(habit.getAvgDailyPractice()).append(" 道\n");
            sb.append("- 偏好题型：").append(habit.getPreferredQuestionType()).append("\n");
            sb.append("- 偏好课程：").append(habit.getPreferredCourse()).append("\n");
            sb.append("- 学习频次：").append(habit.getFrequencyLevel())
                    .append("（").append(habit.getFrequencyDescription()).append("）\n");
            if (habit.getWeeklyTrend() != null) {
                sb.append("- 近 7 天趋势：");
                habit.getWeeklyTrend().forEach(d -> sb.append(d.get("date")).append("=")
                        .append(d.get("total")).append(" "));
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 推荐题目
        if (diagnosis.getDailyRecommendations() != null && !diagnosis.getDailyRecommendations().isEmpty()) {
            sb.append("## 今日推荐题目（").append(diagnosis.getDailyRecommendations().size()).append(" 道）\n");
            for (LearningDiagnosisVO.RecommendedQuestion rq : diagnosis.getDailyRecommendations()) {
                sb.append("- ").append(rq.getQuestionContent())
                        .append(" [").append(rq.getReasonDescription()).append("]\n");
            }
            sb.append("\n");
        }

        sb.append("请基于以上数据，给出个性化的学习建议。");
        return sb.toString();
    }
}
