package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 计算错题分布、知识点错因和周期趋势。 */
@Service
public class LearningDiagnosisErrorPatternAnalyzer {

    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;

    public LearningDiagnosisErrorPatternAnalyzer(QuestionMapper questionMapper, CourseMapper courseMapper) {
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
    }

    public LearningDiagnosisVO.ErrorPatternSummary compute(
            List<WrongQuestion> allWrongs,
            List<PracticeRecord> allRecords,
            List<KnowledgePoint> allPoints,
            Map<Long, Set<Long>> questionToKps) {
        LearningDiagnosisVO.ErrorPatternSummary summary = new LearningDiagnosisVO.ErrorPatternSummary();

        Map<String, Integer> masteryDistribution = new LinkedHashMap<>();
        masteryDistribution.put("未掌握", (int) allWrongs.stream()
                .filter(wrong -> wrong.getMasteryLevel() != null && wrong.getMasteryLevel() == 0)
                .count());
        masteryDistribution.put("部分掌握", (int) allWrongs.stream()
                .filter(wrong -> wrong.getMasteryLevel() != null && wrong.getMasteryLevel() == 1)
                .count());
        masteryDistribution.put("已掌握", (int) allWrongs.stream()
                .filter(wrong -> wrong.getMasteryLevel() != null && wrong.getMasteryLevel() == 2)
                .count());
        summary.setMasteryDistribution(masteryDistribution);
        summary.setRepeatedErrorCount((int) allWrongs.stream()
                .filter(wrong -> wrong.getWrongCount() >= 3)
                .count());

        LocalDateTime weekAgo = LocalDate.now().minusDays(7).atStartOfDay();
        summary.setRecentNewWrongCount((int) allWrongs.stream()
                .filter(wrong -> wrong.getCreateTime() != null && wrong.getCreateTime().isAfter(weekAgo))
                .count());

        Map<Long, Question> questionMap = loadQuestions(allWrongs);
        Map<Long, Course> courseMap = loadCourses(questionMap);
        summary.setTopErrorCourses(buildTopErrorCourses(allWrongs, questionMap, courseMap));
        summary.setQuestionTypeDistribution(buildQuestionTypeDistribution(allWrongs, questionMap));
        summary.setDifficultyDistribution(buildDifficultyDistribution(allWrongs, questionMap));
        summary.setKnowledgePointErrors(buildKnowledgePointErrors(
                allWrongs, allRecords, allPoints, questionToKps, courseMap));
        summary.setRepeatedErrors(buildRepeatedErrors(
                allWrongs, allPoints, questionToKps, questionMap, courseMap));
        summary.setWeeklyErrorTrend(buildWeeklyErrorTrend(allWrongs));
        return summary;
    }

    private Map<Long, Question> loadQuestions(List<WrongQuestion> allWrongs) {
        List<Long> questionIds = allWrongs.stream()
                .map(WrongQuestion::getQuestionId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Question> result = new HashMap<>();
        if (!questionIds.isEmpty()) {
            questionMapper.selectList(new LambdaQueryWrapper<Question>().in(Question::getId, questionIds))
                    .forEach(question -> result.put(question.getId(), question));
        }
        return result;
    }

    private Map<Long, Course> loadCourses(Map<Long, Question> questionMap) {
        Set<Long> courseIds = questionMap.values().stream()
                .map(Question::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Course> result = new HashMap<>();
        if (!courseIds.isEmpty()) {
            courseMapper.selectList(new LambdaQueryWrapper<Course>().in(Course::getId, courseIds))
                    .forEach(course -> result.put(course.getId(), course));
        }
        return result;
    }

    private List<LearningDiagnosisVO.CourseErrorCount> buildTopErrorCourses(
            List<WrongQuestion> allWrongs,
            Map<Long, Question> questionMap,
            Map<Long, Course> courseMap) {
        Map<Long, Integer> courseWrongCounts = new HashMap<>();
        for (WrongQuestion wrong : allWrongs) {
            Question question = questionMap.get(wrong.getQuestionId());
            if (question != null && question.getCourseId() != null) {
                courseWrongCounts.merge(question.getCourseId(), 1, Integer::sum);
            }
        }
        return courseWrongCounts.entrySet().stream()
                .map(entry -> {
                    LearningDiagnosisVO.CourseErrorCount item = new LearningDiagnosisVO.CourseErrorCount();
                    item.setCourseId(entry.getKey());
                    Course course = courseMap.get(entry.getKey());
                    item.setCourseName(course != null ? course.getName() : "未知课程");
                    item.setWrongCount(entry.getValue());
                    return item;
                })
                .sorted((left, right) -> Integer.compare(right.getWrongCount(), left.getWrongCount()))
                .limit(5)
                .collect(Collectors.toList());
    }

    private Map<String, Integer> buildQuestionTypeDistribution(
            List<WrongQuestion> allWrongs, Map<Long, Question> questionMap) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (WrongQuestion wrong : allWrongs) {
            Question question = questionMap.get(wrong.getQuestionId());
            if (question != null && question.getQuestionType() != null) {
                result.merge(questionTypeName(question.getQuestionType()), 1, Integer::sum);
            }
        }
        return result;
    }

    private Map<Integer, Integer> buildDifficultyDistribution(
            List<WrongQuestion> allWrongs, Map<Long, Question> questionMap) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (WrongQuestion wrong : allWrongs) {
            Question question = questionMap.get(wrong.getQuestionId());
            if (question != null && question.getDifficulty() != null) {
                counts.merge(question.getDifficulty(), 1, Integer::sum);
            }
        }
        Map<Integer, Integer> result = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private List<LearningDiagnosisVO.KnowledgePointErrorRank> buildKnowledgePointErrors(
            List<WrongQuestion> allWrongs,
            List<PracticeRecord> allRecords,
            List<KnowledgePoint> allPoints,
            Map<Long, Set<Long>> questionToKps,
            Map<Long, Course> courseMap) {
        Map<Long, Integer> wrongCounts = new HashMap<>();
        Map<Long, Integer> totalCounts = new HashMap<>();
        Map<Long, Long> correctCounts = new HashMap<>();
        for (WrongQuestion wrong : allWrongs) {
            Set<Long> knowledgePointIds = questionToKps.get(wrong.getQuestionId());
            if (knowledgePointIds != null) {
                for (Long knowledgePointId : knowledgePointIds) {
                    wrongCounts.merge(knowledgePointId, wrong.getWrongCount(), Integer::sum);
                }
            }
        }
        for (PracticeRecord record : allRecords) {
            if (record.getQuestionId() == null) {
                continue;
            }
            Set<Long> knowledgePointIds = questionToKps.get(record.getQuestionId());
            if (knowledgePointIds != null) {
                for (Long knowledgePointId : knowledgePointIds) {
                    totalCounts.merge(knowledgePointId, 1, Integer::sum);
                    if (record.getIsCorrect() != null && record.getIsCorrect() == 1) {
                        correctCounts.merge(knowledgePointId, 1L, Long::sum);
                    }
                }
            }
        }

        Map<Long, KnowledgePoint> knowledgePointMap = allPoints.stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, point -> point));
        return wrongCounts.entrySet().stream()
                .map(entry -> buildKnowledgePointError(
                        entry, totalCounts, correctCounts, knowledgePointMap, courseMap))
                .sorted((left, right) -> Integer.compare(right.getWrongCount(), left.getWrongCount()))
                .limit(8)
                .collect(Collectors.toList());
    }

    private LearningDiagnosisVO.KnowledgePointErrorRank buildKnowledgePointError(
            Map.Entry<Long, Integer> entry,
            Map<Long, Integer> totalCounts,
            Map<Long, Long> correctCounts,
            Map<Long, KnowledgePoint> knowledgePointMap,
            Map<Long, Course> courseMap) {
        LearningDiagnosisVO.KnowledgePointErrorRank rank =
                new LearningDiagnosisVO.KnowledgePointErrorRank();
        rank.setKnowledgePointId(entry.getKey());
        KnowledgePoint point = knowledgePointMap.get(entry.getKey());
        rank.setKnowledgePointName(point != null ? point.getName() : "未知知识点");
        if (point != null) {
            rank.setCourseId(point.getCourseId());
            Course course = courseMap.get(point.getCourseId());
            if (course == null && point.getCourseId() != null) {
                course = courseMapper.selectById(point.getCourseId());
                if (course != null) {
                    courseMap.put(point.getCourseId(), course);
                }
            }
            rank.setCourseName(course != null ? course.getName() : "未知课程");
        }
        rank.setWrongCount(entry.getValue());
        int total = totalCounts.getOrDefault(entry.getKey(), 0);
        rank.setTotalAttempts(total);
        long correct = correctCounts.getOrDefault(entry.getKey(), 0L);
        rank.setCorrectRate(total == 0 ? 0 : Math.round(correct * 1000.0 / total) / 10.0);
        return rank;
    }

    private List<LearningDiagnosisVO.RepeatedErrorItem> buildRepeatedErrors(
            List<WrongQuestion> allWrongs,
            List<KnowledgePoint> allPoints,
            Map<Long, Set<Long>> questionToKps,
            Map<Long, Question> questionMap,
            Map<Long, Course> courseMap) {
        Map<Long, KnowledgePoint> knowledgePointMap = allPoints.stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, point -> point));
        return allWrongs.stream()
                .filter(wrong -> wrong.getWrongCount() >= 2)
                .sorted((left, right) -> Integer.compare(right.getWrongCount(), left.getWrongCount()))
                .limit(10)
                .map(wrong -> buildRepeatedError(
                        wrong, knowledgePointMap, questionToKps, questionMap, courseMap))
                .collect(Collectors.toList());
    }

    private LearningDiagnosisVO.RepeatedErrorItem buildRepeatedError(
            WrongQuestion wrong,
            Map<Long, KnowledgePoint> knowledgePointMap,
            Map<Long, Set<Long>> questionToKps,
            Map<Long, Question> questionMap,
            Map<Long, Course> courseMap) {
        LearningDiagnosisVO.RepeatedErrorItem item = new LearningDiagnosisVO.RepeatedErrorItem();
        item.setQuestionId(wrong.getQuestionId());
        item.setWrongCount(wrong.getWrongCount());
        item.setMasteryLevel(wrong.getMasteryLevel());
        item.setLastWrongAnswer(wrong.getLastWrongAnswer());

        Question question = questionMap.get(wrong.getQuestionId());
        if (question != null) {
            item.setQuestionContent(truncate(question.getContent(), 120));
            item.setQuestionType(questionTypeName(question.getQuestionType()));
            item.setDifficulty(question.getDifficulty());
            if (question.getCourseId() != null) {
                Course course = courseMap.get(question.getCourseId());
                item.setCourseName(course != null ? course.getName() : null);
            }
        }
        Set<Long> knowledgePointIds = questionToKps.get(wrong.getQuestionId());
        if (knowledgePointIds != null && !knowledgePointIds.isEmpty()) {
            KnowledgePoint point = knowledgePointMap.get(knowledgePointIds.iterator().next());
            item.setKnowledgePointName(point != null ? point.getName() : null);
        }
        return item;
    }

    private List<Map<String, Object>> buildWeeklyErrorTrend(List<WrongQuestion> allWrongs) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int week = 3; week >= 0; week--) {
            LocalDate weekStart = today.minusWeeks(week).with(java.time.DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusDays(6);
            LocalDateTime start = weekStart.atStartOfDay();
            LocalDateTime end = weekEnd.atTime(LocalTime.MAX);
            long count = allWrongs.stream()
                    .filter(wrong -> wrong.getCreateTime() != null
                            && !wrong.getCreateTime().isBefore(start)
                            && !wrong.getCreateTime().isAfter(end))
                    .count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("weekStart", weekStart.toString());
            item.put("weekEnd", weekEnd.toString());
            item.put("label", weekStart.getMonthValue() + "/" + weekStart.getDayOfMonth()
                    + "-" + weekEnd.getMonthValue() + "/" + weekEnd.getDayOfMonth());
            item.put("count", count);
            trend.add(item);
        }
        return trend;
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

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
