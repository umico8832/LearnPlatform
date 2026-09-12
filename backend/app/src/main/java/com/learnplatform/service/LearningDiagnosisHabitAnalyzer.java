package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 计算学习活跃度、习惯趋势和每日规则建议。 */
@Service
public class LearningDiagnosisHabitAnalyzer {

    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;

    public LearningDiagnosisHabitAnalyzer(QuestionMapper questionMapper, CourseMapper courseMapper) {
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
    }

    public int calculateStreak(List<PracticeRecord> records) {
        if (records.isEmpty()) {
            return 0;
        }
        Set<LocalDate> dates = records.stream()
                .filter(record -> record.getCreateTime() != null)
                .map(record -> record.getCreateTime().toLocalDate())
                .collect(Collectors.toSet());
        int streak = countBackwardFrom(LocalDate.now(), dates);
        return streak == 0 ? countBackwardFrom(LocalDate.now().minusDays(1), dates) : streak;
    }

    public int calculateActiveDays(List<PracticeRecord> records, int days) {
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        return (int) records.stream()
                .filter(record -> record.getCreateTime() != null && record.getCreateTime().isAfter(since))
                .map(record -> record.getCreateTime().toLocalDate())
                .distinct()
                .count();
    }

    public LearningDiagnosisVO.LearningHabit computeLearningHabit(List<PracticeRecord> allRecords) {
        LearningDiagnosisVO.LearningHabit habit = new LearningDiagnosisVO.LearningHabit();
        if (allRecords.isEmpty()) {
            habit.setAvgDailyPractice(0);
            habit.setPreferredQuestionType("暂无数据");
            habit.setPreferredCourse("暂无数据");
            habit.setFrequencyLevel("INACTIVE");
            habit.setFrequencyDescription("暂无学习记录，开始你的第一道题吧！");
            habit.setWeeklyTrend(buildEmptyWeeklyTrend());
            return habit;
        }

        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(30).atStartOfDay();
        long recent30Count = allRecords.stream()
                .filter(record -> record.getCreateTime() != null
                        && record.getCreateTime().isAfter(thirtyDaysAgo))
                .count();
        habit.setAvgDailyPractice(Math.round(recent30Count / 30.0 * 10.0) / 10.0);
        habit.setPreferredQuestionType(findPreferredQuestionType(allRecords));
        habit.setPreferredCourse(findPreferredCourse(allRecords));

        int activeDays = calculateActiveDays(allRecords, 30);
        if (activeDays >= 20) {
            habit.setFrequencyLevel("ACTIVE");
            habit.setFrequencyDescription("近 30 天学习 " + activeDays + " 天，学习习惯很好！");
        } else if (activeDays >= 10) {
            habit.setFrequencyLevel("MODERATE");
            habit.setFrequencyDescription("近 30 天学习 " + activeDays + " 天，坚持每天练习效果更好。");
        } else {
            habit.setFrequencyLevel("INACTIVE");
            habit.setFrequencyDescription("近 30 天仅学习 " + activeDays + " 天，建议增加学习频率。");
        }
        habit.setWeeklyTrend(buildWeeklyTrend(allRecords));
        return habit;
    }

    public String generateDailyAdvice(LearningDiagnosisVO diagnosis) {
        StringBuilder advice = new StringBuilder();
        if (diagnosis.getStreakDays() >= 7) {
            advice.append("🔥 连续学习 ").append(diagnosis.getStreakDays()).append(" 天，非常好！继续保持。\n\n");
        } else if (diagnosis.getStreakDays() >= 3) {
            advice.append("📈 连续学习 ").append(diagnosis.getStreakDays()).append(" 天，坚持下去会更好。\n\n");
        } else if (diagnosis.getStreakDays() == 0) {
            advice.append("💡 今天还没有开始学习，每天练习几道题效果更好。\n\n");
        }

        if (diagnosis.getWeakPoints() != null && !diagnosis.getWeakPoints().isEmpty()) {
            LearningDiagnosisVO.WeakPoint top = diagnosis.getWeakPoints().get(0);
            advice.append("📚 重点关注：").append(top.getKnowledgePointName())
                    .append("（").append(top.getCourseName()).append("）");
            if ("NOT_STARTED".equals(top.getMasteryStatus())) {
                advice.append("尚未开始练习，建议先学习核心概念。\n\n");
            } else {
                advice.append("，正确率仅 ")
                        .append(String.format("%.1f%%", top.getCorrectRate()))
                        .append("。\n\n");
            }
        }

        if (diagnosis.getErrorPatterns() != null
                && diagnosis.getErrorPatterns().getRepeatedErrorCount() > 0) {
            advice.append("⚠️ 有 ").append(diagnosis.getErrorPatterns().getRepeatedErrorCount())
                    .append(" 道题反复出错，建议使用 AI 讲解理解后再练习。\n\n");
        }
        if (diagnosis.getLearningHabit() != null
                && "INACTIVE".equals(diagnosis.getLearningHabit().getFrequencyLevel())) {
            advice.append("⏰ ").append(diagnosis.getLearningHabit().getFrequencyDescription()).append("\n");
        }
        if (advice.length() == 0) {
            advice.append("✅ 学习状态良好，继续按计划练习吧！可以尝试更高难度的题目提升自己。");
        }
        return advice.toString().trim();
    }

    private String findPreferredQuestionType(List<PracticeRecord> allRecords) {
        Map<String, Long> typeCounts = new HashMap<>();
        for (PracticeRecord record : allRecords) {
            Question question = questionMapper.selectById(record.getQuestionId());
            if (question != null && question.getQuestionType() != null) {
                typeCounts.merge(question.getQuestionType(), 1L, Long::sum);
            }
        }
        return typeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .map(this::questionTypeName)
                .orElse("暂无数据");
    }

    private String findPreferredCourse(List<PracticeRecord> allRecords) {
        Set<Long> questionIds = allRecords.stream()
                .map(PracticeRecord::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Question> questionMap = new HashMap<>();
        if (!questionIds.isEmpty()) {
            questionMapper.selectList(new LambdaQueryWrapper<Question>().in(Question::getId, questionIds))
                    .forEach(question -> questionMap.put(question.getId(), question));
        }
        Map<Long, Long> courseCounts = new HashMap<>();
        for (PracticeRecord record : allRecords) {
            Question question = questionMap.get(record.getQuestionId());
            if (question != null && question.getCourseId() != null) {
                courseCounts.merge(question.getCourseId(), 1L, Long::sum);
            }
        }
        Long topCourseId = courseCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (topCourseId == null) {
            return "暂无数据";
        }
        Course course = courseMapper.selectById(topCourseId);
        return course != null ? course.getName() : "未知课程";
    }

    private List<Map<String, Object>> buildWeeklyTrend(List<PracticeRecord> allRecords) {
        LocalDate today = LocalDate.now();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        Map<LocalDate, List<PracticeRecord>> grouped = allRecords.stream()
                .filter(record -> record.getCreateTime() != null
                        && record.getCreateTime().isAfter(weekStart))
                .collect(Collectors.groupingBy(record -> record.getCreateTime().toLocalDate()));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int day = 6; day >= 0; day--) {
            LocalDate date = today.minusDays(day);
            List<PracticeRecord> records = grouped.getOrDefault(date, Collections.emptyList());
            long correct = records.stream()
                    .filter(record -> record.getIsCorrect() != null && record.getIsCorrect() == 1)
                    .count();
            trend.add(buildTrendItem(date, records.size(), correct));
        }
        return trend;
    }

    private List<Map<String, Object>> buildEmptyWeeklyTrend() {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int day = 6; day >= 0; day--) {
            trend.add(buildTrendItem(today.minusDays(day), 0, 0));
        }
        return trend;
    }

    private Map<String, Object> buildTrendItem(LocalDate date, int total, long correct) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("date", date.toString());
        item.put("total", total);
        item.put("correct", correct);
        item.put("wrong", total - correct);
        return item;
    }

    private int countBackwardFrom(LocalDate start, Set<LocalDate> dates) {
        int streak = 0;
        LocalDate date = start;
        while (dates.contains(date)) {
            streak++;
            date = date.minusDays(1);
        }
        return streak;
    }

    private String questionTypeName(String questionType) {
        if (questionType == null) {
            return "未知";
        }
        return switch (questionType) {
            case "SINGLE_CHOICE" -> "单选题";
            case "MULTIPLE_CHOICE" -> "多选题";
            case "TRUE_FALSE" -> "判断题";
            case "FILL_BLANK" -> "填空题";
            case "SHORT_ANSWER" -> "简答题";
            default -> questionType;
        };
    }
}
