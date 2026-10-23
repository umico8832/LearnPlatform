package com.learnplatform.config;

import com.learnplatform.dto.AdminStatisticsVO;
import com.learnplatform.dto.GlobalSearchResultVO;
import com.learnplatform.dto.KnowledgeGraphVO;
import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.dto.LearningPathVO;
import com.learnplatform.dto.LearningReportVO;
import com.learnplatform.dto.QuestionReviewSuggestionVO;
import com.learnplatform.dto.StatisticsVO;
import com.learnplatform.dto.SubmissionDifficultyVO;
import com.learnplatform.dto.SubmissionKPTaggingVO;
import com.learnplatform.dto.SubmissionQualityCheckVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Representative values returned by every Redis-backed cache. */
final class RedisCacheFixtures {

    private RedisCacheFixtures() {
    }

    static Map<String, Object> values() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("statistics", statistics());
        values.put("adminStatistics", adminStatistics());
        values.put("dailyTrend", dailyTrends());
        values.put("courseStats", courseStats());
        values.put("learningReport", learningReport());
        values.put("learningPath", learningPath());
        values.put("knowledgeGraph", knowledgeGraph());
        values.put("learningDiagnosis", learningDiagnosis());
        values.put("submissionQuality", submissionQuality());
        values.put("submissionKPTagging", submissionKPTagging());
        values.put("submissionDifficulty", submissionDifficulty());
        values.put("questionReviewSuggestion", questionReviewSuggestion());
        values.put("globalSearch", globalSearch());
        return values;
    }

    static void assertSameValue(Object expected, Object actual) {
        assertThat(actual).isNotNull().isExactlyInstanceOf(expected.getClass());
        assertThat(actual).usingRecursiveComparison()
                .withStrictTypeChecking()
                .isEqualTo(expected);
    }

    private static StatisticsVO statistics() {
        StatisticsVO value = new StatisticsVO();
        value.setTotalPractice(12L);
        value.setCorrectCount(9L);
        value.setWrongCount(3L);
        value.setCorrectRate(75.0);
        value.setTodayPractice(2L);
        value.setStreakDays(4);
        value.setWrongQuestionCount(3L);
        value.setMasteredCount(1L);
        return value;
    }

    private static AdminStatisticsVO adminStatistics() {
        AdminStatisticsVO value = new AdminStatisticsVO();
        value.setTotalUsers(101L);
        value.setEnabledUsers(99L);
        value.setTotalQuestions(88L);
        value.setWeeklyNewQuestions(7L);
        value.setTotalExamPapers(9L);
        value.setPublishedExamPapers(6L);
        value.setDraftExamPapers(3L);
        value.setTodayActiveUsers(18L);
        value.setTotalPracticeRecords(321L);
        value.setQuestionTypeDistribution(linkedMap("单选题", 40L, "简答题", 8L));
        value.setDailyActivity(list(new AdminStatisticsVO.DailyActivity("2026-10-21", 14L, 6L)));
        return value;
    }

    private static LearningReportVO learningReport() {
        LearningReportVO value = new LearningReportVO();
        value.setMonthTotalPractice(16);
        value.setMonthCorrectCount(12);
        value.setMonthCorrectRate(75.0);
        value.setMonthNewWrongCount(4);
        value.setMonthMasteredCount(2);
        value.setMonthExamCount(1);
        value.setMonthExamAvgScore(86.5);
        value.setLastMonthTotalPractice(10);
        value.setLastMonthCorrectRate(70.0);
        value.setPracticeGrowthRate(60.0);
        value.setCorrectRateChange(5.0);
        value.setLearningEffectScore(82.5);
        value.setLearningEffectLevel("IMPROVING");
        value.setLearningEffectLabel("稳步提升");
        value.setLearningEffectSummary("继续复习薄弱知识点");
        value.setWrongQuestionConversionRate(33.3);
        value.setReviewMasteryRate(50.0);
        value.setActiveStudyDays(6);
        value.setDailyTrend(dailyTrends());
        value.setCourseStats(courseStats());
        value.setQuestionTypeDistribution(linkedMap("单选题", 6, "简答题", 2));
        value.setTotalReviewCards(10);
        value.setMonthlyReviewedCount(7);
        value.setReviewStreakDays(3);
        value.setMasteredReviewCards(4);
        value.setDueTodayCount(2);
        value.setMonthlyReviewTrend(list(1, 2, 3));
        return value;
    }

    private static LearningPathVO learningPath() {
        LearningPathVO.PathStep step = new LearningPathVO.PathStep();
        step.setOrder(1);
        step.setKnowledgePointId(101L);
        step.setKnowledgePointName("集合");
        step.setCourseId(10L);
        step.setCourseName("Java");
        step.setParentId(100L);
        step.setCorrectRate(66.7);
        step.setTotalAttempts(6);
        step.setWrongCount(2);
        step.setMasteryStatus("NEEDS_REVIEW");
        step.setPriorityScore(78.5);
        step.setRecommendation("复习集合框架");
        LearningPathVO.CourseOverview overview = new LearningPathVO.CourseOverview();
        overview.setCourseId(10L);
        overview.setCourseName("Java");
        overview.setCorrectRate(75.0);
        overview.setTotalAttempts(12);
        overview.setKnowledgePointCount(4);
        overview.setMasteredPointCount(2);
        LearningPathVO value = new LearningPathVO();
        value.setCourseName("Java");
        value.setOverallMastery(75.0);
        value.setTotalKnowledgePoints(4);
        value.setMasteredCount(2);
        value.setWeakCount(1);
        value.setSteps(list(step));
        value.setCourseOverviews(list(overview));
        return value;
    }

    private static KnowledgeGraphVO knowledgeGraph() {
        KnowledgeGraphVO.GraphNode node = new KnowledgeGraphVO.GraphNode();
        node.setId(101L);
        node.setName("集合");
        node.setCourseId(10L);
        node.setCourseName("Java");
        node.setParentId(100L);
        node.setNodeType("leaf");
        node.setMasteryLevel(2);
        node.setAccuracy(66.7);
        node.setPracticeCount(6);
        node.setWrongCount(2);
        node.setCategory("Java");
        KnowledgeGraphVO value = new KnowledgeGraphVO();
        value.setNodes(list(node));
        value.setEdges(list(new KnowledgeGraphVO.GraphEdge(100L, 101L, "parent-child")));
        value.setCourses(list(new KnowledgeGraphVO.CourseInfo(10L, "Java")));
        return value;
    }

    private static LearningDiagnosisVO learningDiagnosis() {
        LearningDiagnosisVO.WeakPoint weakPoint = new LearningDiagnosisVO.WeakPoint();
        weakPoint.setKnowledgePointId(101L);
        weakPoint.setKnowledgePointName("集合");
        weakPoint.setCourseId(10L);
        weakPoint.setCourseName("Java");
        weakPoint.setCorrectRate(66.7);
        weakPoint.setTotalAttempts(6);
        weakPoint.setWrongCount(2);
        weakPoint.setMasteryStatus("WEAK");
        weakPoint.setPriorityScore(78.5);
        weakPoint.setDiagnosis("复习集合框架");
        LearningDiagnosisVO.CourseMastery course = new LearningDiagnosisVO.CourseMastery();
        course.setCourseId(10L);
        course.setCourseName("Java");
        course.setCorrectRate(75.0);
        course.setTotalAttempts(12);
        course.setWrongCount(3);
        course.setKnowledgePointCount(4);
        course.setWeakPointCount(1);
        LearningDiagnosisVO value = new LearningDiagnosisVO();
        value.setTotalPractice(12);
        value.setOverallCorrectRate(75.0);
        value.setActiveDaysLast30(6);
        value.setStreakDays(3);
        value.setWeakPoints(list(weakPoint));
        value.setCourseMasteries(list(course));
        value.setErrorPatterns(errorPatterns());
        value.setLearningHabit(learningHabit());
        value.setDailyAdvice("先完成集合复习");
        return value;
    }

    private static LearningDiagnosisVO.ErrorPatternSummary errorPatterns() {
        LearningDiagnosisVO.CourseErrorCount course = new LearningDiagnosisVO.CourseErrorCount();
        course.setCourseId(10L);
        course.setCourseName("Java");
        course.setWrongCount(3);
        LearningDiagnosisVO.KnowledgePointErrorRank point = new LearningDiagnosisVO.KnowledgePointErrorRank();
        point.setKnowledgePointId(101L);
        point.setKnowledgePointName("集合");
        point.setCourseId(10L);
        point.setCourseName("Java");
        point.setWrongCount(2);
        point.setTotalAttempts(6);
        point.setCorrectRate(66.7);
        LearningDiagnosisVO.RepeatedErrorItem repeated = new LearningDiagnosisVO.RepeatedErrorItem();
        repeated.setQuestionId(301L);
        repeated.setQuestionContent("集合遍历");
        repeated.setQuestionType("SINGLE_CHOICE");
        repeated.setDifficulty(3);
        repeated.setWrongCount(2);
        repeated.setMasteryLevel(1);
        repeated.setLastWrongAnswer("B");
        repeated.setKnowledgePointName("集合");
        repeated.setCourseName("Java");
        LearningDiagnosisVO.ErrorPatternSummary value = new LearningDiagnosisVO.ErrorPatternSummary();
        value.setTopErrorCourses(list(course));
        value.setMasteryDistribution(linkedMap("未掌握", 2, "已掌握", 1));
        value.setRepeatedErrorCount(1);
        value.setRecentNewWrongCount(2);
        value.setQuestionTypeDistribution(linkedMap("单选题", 3, "简答题", 1));
        value.setDifficultyDistribution(linkedMap(2, 1, 3, 2));
        value.setKnowledgePointErrors(list(point));
        value.setRepeatedErrors(list(repeated));
        value.setWeeklyErrorTrend(weeklyErrorTrend());
        return value;
    }

    private static LearningDiagnosisVO.LearningHabit learningHabit() {
        LearningDiagnosisVO.LearningHabit value = new LearningDiagnosisVO.LearningHabit();
        value.setAvgDailyPractice(2.5);
        value.setPreferredQuestionType("单选题");
        value.setPreferredCourse("Java");
        value.setWeeklyTrend(dailyTrends());
        value.setFrequencyLevel("ACTIVE");
        value.setFrequencyDescription("最近学习活跃");
        return value;
    }

    private static SubmissionQualityCheckVO submissionQuality() {
        SubmissionQualityCheckVO value = new SubmissionQualityCheckVO();
        value.setQualityScore(88);
        value.setSummary("题目完整");
        value.setRecommendation("APPROVE");
        value.setFormatCheck(new SubmissionQualityCheckVO.CheckItem("PASS", "格式正确"));
        value.setCompletenessCheck(new SubmissionQualityCheckVO.CheckItem("PASS", "信息完整"));
        value.setAnswerCheck(new SubmissionQualityCheckVO.CheckItem("WARNING", "需复核答案"));
        value.setAnalysisCheck(new SubmissionQualityCheckVO.CheckItem("PASS", "解析充分"));
        value.setKnowledgePointCheck(new SubmissionQualityCheckVO.CheckItem("PASS", "知识点匹配"));
        value.setRiskPoints(list("术语需统一"));
        value.setSuggestions(list("补充边界条件"));
        return value;
    }

    private static SubmissionKPTaggingVO submissionKPTagging() {
        return new SubmissionKPTaggingVO(
                list(new SubmissionKPTaggingVO.TaggedKP(101L, "集合", "Java", "HIGH", "考查集合遍历")),
                "推荐集合知识点", "101");
    }

    private static SubmissionDifficultyVO submissionDifficulty() {
        SubmissionDifficultyVO value = new SubmissionDifficultyVO();
        value.setSuggestedDifficulty(3);
        value.setOriginalDifficulty(2);
        value.setDifficultyMatch(false);
        value.setConfidence("HIGH");
        value.setReason("需要理解嵌套集合");
        value.setCognitiveLevel("应用");
        value.setFactors(list(new SubmissionDifficultyVO.DifficultyFactor("嵌套", "需组合多个概念", "INCREASE")));
        value.setSummary("中等难度");
        return value;
    }

    private static QuestionReviewSuggestionVO questionReviewSuggestion() {
        QuestionReviewSuggestionVO value = new QuestionReviewSuggestionVO();
        value.setRecommendation("REVISE");
        value.setConfidenceScore(82);
        value.setSummary("题干需要澄清");
        value.setSuggestedContent("请说明集合的遍历结果。");
        value.setSuggestedDifficulty(3);
        value.setRiskPoints(list("边界条件不清"));
        value.setSuggestions(list("明确初始状态"));
        value.setAnswerAnalysis("答案方向正确");
        value.setKnowledgeAnalysis("符合集合知识点");
        return value;
    }

    private static GlobalSearchResultVO globalSearch() {
        GlobalSearchResultVO.SearchItem question = new GlobalSearchResultVO.SearchItem(
                301L, "集合遍历", "单选题", "QUESTION", "/questions?questionId=301");
        question.setHighlight("遍历");
        return new GlobalSearchResultVO(list(question),
                list(new GlobalSearchResultVO.SearchItem(10L, "Java", "基础课程", "COURSE", "/courses/10")),
                list(new GlobalSearchResultVO.SearchItem(101L, "集合", "核心知识点", "KNOWLEDGE_POINT", "/questions?knowledgePointId=101")));
    }

    private static List<Map<String, Object>> dailyTrends() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", "2026-10-21");
        row.put("total", 11);
        row.put("correct", 8L);
        row.put("wrong", 3L);
        return list(row);
    }

    private static List<Map<String, Object>> courseStats() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("courseId", 10L);
        row.put("courseName", "Java");
        row.put("total", 7);
        row.put("correct", 5L);
        row.put("correctRate", 71.4);
        return list(row);
    }

    private static List<Map<String, Object>> weeklyErrorTrend() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("weekStart", "2026-10-12");
        row.put("weekEnd", "2026-10-18");
        row.put("label", "10/12-10/18");
        row.put("count", 3L);
        return list(row);
    }

    @SafeVarargs
    private static <T> List<T> list(T... values) {
        return new ArrayList<>(List.of(values));
    }

    private static <K, V> Map<K, V> linkedMap(K firstKey, V firstValue, K secondKey, V secondValue) {
        Map<K, V> values = new LinkedHashMap<>();
        values.put(firstKey, firstValue);
        values.put(secondKey, secondValue);
        return values;
    }
}
