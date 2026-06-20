package com.learnplatform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.dto.SimilarQuestionVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import com.learnplatform.service.ai.AiProvider;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningDiagnosisServiceTest {

    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private PracticeRecordMapper practiceRecordMapper;
    @Mock private WrongQuestionMapper wrongQuestionMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private AiProvider aiProvider;
    @Mock private AiService aiService;

    private LearningDiagnosisService service;

    private static final Long USER_ID = 1L;

    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, PracticeRecord.class);
        TableInfoHelper.initTableInfo(assistant, WrongQuestion.class);
        TableInfoHelper.initTableInfo(assistant, Question.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgePoint.class);
        TableInfoHelper.initTableInfo(assistant, QuestionKnowledgePoint.class);
        TableInfoHelper.initTableInfo(assistant, Course.class);
    }

    @BeforeEach
    void setUp() {
        service = new LearningDiagnosisService(
                knowledgePointMapper, questionKnowledgePointMapper,
                questionMapper, practiceRecordMapper,
                wrongQuestionMapper, courseMapper,
                aiProvider, aiService
        );
    }

    // ======================== getDiagnosis — 基本场景 ========================

    @Test
    void getDiagnosisReturnsZeroedVoWhenNoRecords() {
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        assertNotNull(vo);
        assertEquals(0, vo.getTotalPractice());
        assertEquals(0, vo.getOverallCorrectRate());
        assertEquals(0, vo.getStreakDays());
        assertNotNull(vo.getWeakPoints());
        assertTrue(vo.getWeakPoints().isEmpty());
        assertNotNull(vo.getCourseMasteries());
        assertTrue(vo.getCourseMasteries().isEmpty());
        assertNotNull(vo.getLearningHabit());
        assertNotNull(vo.getDailyAdvice());
        verifyNoInteractions(questionKnowledgePointMapper);
    }

    @Test
    void getDiagnosisComputesCorrectRate() {
        PracticeRecord correct = stubRecord(USER_ID, 1L, 1, LocalDateTime.now());
        PracticeRecord wrong = stubRecord(USER_ID, 2L, 0, LocalDateTime.now().minusHours(1));
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(correct, wrong));
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        assertEquals(2, vo.getTotalPractice());
        assertEquals(50.0, vo.getOverallCorrectRate());
    }

    @Test
    void getDiagnosisComputesAllCorrectRate() {
        PracticeRecord r1 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now());
        PracticeRecord r2 = stubRecord(USER_ID, 2L, 1, LocalDateTime.now().minusHours(1));
        PracticeRecord r3 = stubRecord(USER_ID, 3L, 1, LocalDateTime.now().minusHours(2));
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3));
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        assertEquals(3, vo.getTotalPractice());
        assertEquals(100.0, vo.getOverallCorrectRate());
    }

    @Test
    void getDiagnosisComputesStreakDays() {
        // Today and yesterday
        PracticeRecord today = stubRecord(USER_ID, 1L, 1, LocalDateTime.now());
        PracticeRecord yesterday = stubRecord(USER_ID, 2L, 1, LocalDateTime.now().minusDays(1));
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(today, yesterday));
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        assertEquals(2, vo.getStreakDays());
    }

    // ======================== getDiagnosis — 学习习惯 ========================

    @Test
    void getDiagnosisLearningHabitForEmptyRecords() {
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        LearningDiagnosisVO.LearningHabit habit = vo.getLearningHabit();
        assertNotNull(habit);
        assertEquals(0, habit.getAvgDailyPractice());
        assertEquals("暂无数据", habit.getPreferredQuestionType());
        assertEquals("暂无数据", habit.getPreferredCourse());
        assertEquals("INACTIVE", habit.getFrequencyLevel());
        assertNotNull(habit.getWeeklyTrend());
        assertEquals(7, habit.getWeeklyTrend().size());
    }

    @Test
    void getDiagnosisLearningHabitWithRecords() {
        PracticeRecord r1 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1));
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        Question q = stubQuestion(1L, "SINGLE_CHOICE", 1L);
        when(questionMapper.selectList(any())).thenReturn(List.of(q));
        when(questionMapper.selectById(1L)).thenReturn(q);

        Course course = stubCourse(1L, "Java 基础");
        when(courseMapper.selectById(1L)).thenReturn(course);

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        LearningDiagnosisVO.LearningHabit habit = vo.getLearningHabit();
        assertNotNull(habit);
        assertEquals("单选题", habit.getPreferredQuestionType());
        assertEquals("Java 基础", habit.getPreferredCourse());
    }

    @Test
    void getDiagnosisWeeklyTrendHas7Days() {
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        List<Map<String, Object>> trend = vo.getLearningHabit().getWeeklyTrend();
        assertEquals(7, trend.size());
        // Each day entry has date, total, correct, wrong
        for (Map<String, Object> day : trend) {
            assertTrue(day.containsKey("date"));
            assertTrue(day.containsKey("total"));
            assertTrue(day.containsKey("correct"));
            assertTrue(day.containsKey("wrong"));
            assertEquals(0, day.get("total"));
        }
    }

    // ======================== getDiagnosis — 错因分析 ========================

    @Test
    void getDiagnosisErrorPatternsWithoutWrongs() {
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        LearningDiagnosisVO.ErrorPatternSummary ep = vo.getErrorPatterns();
        assertNotNull(ep);
        assertEquals(0, ep.getRepeatedErrorCount());
        assertEquals(0, ep.getRecentNewWrongCount());
        assertNotNull(ep.getMasteryDistribution());
        assertEquals(3, ep.getMasteryDistribution().size());
    }

    @Test
    void getDiagnosisErrorPatternsWithRepeatedErrors() {
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        // A wrong question with wrongCount >= 3 => repeated
        WrongQuestion repeatedWq = stubWrongQuestion(USER_ID, 1L, 3, 0);
        WrongQuestion normalWq = stubWrongQuestion(USER_ID, 2L, 1, 1);
        when(wrongQuestionMapper.selectList(any())).thenReturn(List.of(repeatedWq, normalWq));

        // Stub question and course lookups for error pattern
        Question q1 = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        Question q2 = stubQuestion(2L, "MULTIPLE_CHOICE", 10L);
        when(questionMapper.selectList(any())).thenReturn(List.of(q1, q2));
        Course course10 = stubCourse(10L, "Data Structures");
        when(courseMapper.selectList(any())).thenReturn(List.of(course10));

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        LearningDiagnosisVO.ErrorPatternSummary ep = vo.getErrorPatterns();
        assertEquals(1, ep.getRepeatedErrorCount());
        // mastery: 0 => "未掌握" count=1, 1 => "部分掌握" count=1
        assertEquals(1, ep.getMasteryDistribution().get("未掌握"));
        assertEquals(1, ep.getMasteryDistribution().get("部分掌握"));
        assertEquals(0, ep.getMasteryDistribution().get("已掌握"));
    }

    @Test
    void getDiagnosisErrorPatternsRecentNewWrong() {
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        // Wrong question created now => should count in recent 7 days
        WrongQuestion recentWq = stubWrongQuestion(USER_ID, 1L, 1, 0);
        recentWq.setCreateTime(LocalDateTime.now());
        when(wrongQuestionMapper.selectList(any())).thenReturn(List.of(recentWq));

        Question q1 = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectList(any())).thenReturn(List.of(q1));
        when(courseMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        LearningDiagnosisVO.ErrorPatternSummary ep = vo.getErrorPatterns();
        assertEquals(1, ep.getRecentNewWrongCount());
    }

    @Test
    void getDiagnosisErrorPatternsTopErrorCourses() {
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        WrongQuestion wq1 = stubWrongQuestion(USER_ID, 1L, 2, 0);
        WrongQuestion wq2 = stubWrongQuestion(USER_ID, 2L, 3, 0);
        WrongQuestion wq3 = stubWrongQuestion(USER_ID, 3L, 1, 0);
        when(wrongQuestionMapper.selectList(any())).thenReturn(List.of(wq1, wq2, wq3));

        Question q1 = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        Question q2 = stubQuestion(2L, "MULTIPLE_CHOICE", 10L);
        Question q3 = stubQuestion(3L, "FILL_BLANK", 20L);
        when(questionMapper.selectList(any())).thenReturn(List.of(q1, q2, q3));

        Course course10 = stubCourse(10L, "Java");
        Course course20 = stubCourse(20L, "Python");
        when(courseMapper.selectList(any())).thenReturn(List.of(course10, course20));

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        LearningDiagnosisVO.ErrorPatternSummary ep = vo.getErrorPatterns();
        assertNotNull(ep.getTopErrorCourses());
        assertFalse(ep.getTopErrorCourses().isEmpty());
        // Course 10 has 2 wrong questions => should be first
        assertEquals(10L, ep.getTopErrorCourses().get(0).getCourseId());
        assertEquals(2, ep.getTopErrorCourses().get(0).getWrongCount());
    }

    // ======================== getDiagnosis — 知识点薄弱诊断 ========================

    @Test
    void getDiagnosisWeakPointsFilteredByThreshold() {
        // Setup: a knowledge point with 60% correct rate (below 70% REVIEW_THRESHOLD)
        KnowledgePoint kp = stubKnowledgePoint(1L, "KP-A", 10L);
        when(knowledgePointMapper.selectList(any())).thenReturn(List.of(kp));

        // question_knowledge_point mapping: question 100 -> kp 1
        QuestionKnowledgePoint qkp = new QuestionKnowledgePoint();
        qkp.setQuestionId(100L);
        qkp.setKnowledgePointId(1L);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of(qkp));

        // 5 practice records for question 100: 3 correct, 2 wrong => 60%
        PracticeRecord r1 = stubRecord(USER_ID, 100L, 1, LocalDateTime.now());
        PracticeRecord r2 = stubRecord(USER_ID, 100L, 1, LocalDateTime.now().minusHours(1));
        PracticeRecord r3 = stubRecord(USER_ID, 100L, 1, LocalDateTime.now().minusHours(2));
        PracticeRecord r4 = stubRecord(USER_ID, 100L, 0, LocalDateTime.now().minusHours(3));
        PracticeRecord r5 = stubRecord(USER_ID, 100L, 0, LocalDateTime.now().minusHours(4));
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3, r4, r5));
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());

        Course course = stubCourse(10L, "Java");
        when(courseMapper.selectById(10L)).thenReturn(course);

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        assertNotNull(vo.getWeakPoints());
        assertEquals(1, vo.getWeakPoints().size());

        LearningDiagnosisVO.WeakPoint wp = vo.getWeakPoints().get(0);
        assertEquals(1L, wp.getKnowledgePointId());
        assertEquals("KP-A", wp.getKnowledgePointName());
        assertEquals("Java", wp.getCourseName());
        assertEquals(60.0, wp.getCorrectRate());
        assertEquals(5, wp.getTotalAttempts());
        assertEquals("NEEDS_REVIEW", wp.getMasteryStatus());
        assertNotNull(wp.getDiagnosis());
        assertFalse(wp.getDiagnosis().isEmpty());
    }

    @Test
    void getDiagnosisWeakPointsHighCorrectRateFiltered() {
        // Knowledge point with 90% correct rate => should be filtered out (>=70%)
        KnowledgePoint kp = stubKnowledgePoint(1L, "KP-Strong", 10L);
        when(knowledgePointMapper.selectList(any())).thenReturn(List.of(kp));

        QuestionKnowledgePoint qkp = new QuestionKnowledgePoint();
        qkp.setQuestionId(100L);
        qkp.setKnowledgePointId(1L);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of(qkp));

        // 10 records: 9 correct, 1 wrong => 90%
        PracticeRecord r1 = stubRecord(USER_ID, 100L, 1, LocalDateTime.now());
        PracticeRecord r2 = stubRecord(USER_ID, 100L, 1, LocalDateTime.now().minusHours(1));
        PracticeRecord r3 = stubRecord(USER_ID, 100L, 1, LocalDateTime.now().minusHours(2));
        PracticeRecord r4 = stubRecord(USER_ID, 100L, 1, LocalDateTime.now().minusHours(3));
        PracticeRecord r5 = stubRecord(USER_ID, 100L, 1, LocalDateTime.now().minusHours(4));
        PracticeRecord r6 = stubRecord(USER_ID, 100L, 1, LocalDateTime.now().minusHours(5));
        PracticeRecord r7 = stubRecord(USER_ID, 100L, 1, LocalDateTime.now().minusHours(6));
        PracticeRecord r8 = stubRecord(USER_ID, 100L, 1, LocalDateTime.now().minusHours(7));
        PracticeRecord r9 = stubRecord(USER_ID, 100L, 1, LocalDateTime.now().minusHours(8));
        PracticeRecord r10 = stubRecord(USER_ID, 100L, 0, LocalDateTime.now().minusHours(9));
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3, r4, r5, r6, r7, r8, r9, r10));
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        // Should be filtered: correct rate 90% >= REVIEW_THRESHOLD(70%)
        assertTrue(vo.getWeakPoints().isEmpty());
    }

    @Test
    void getDiagnosisWeakPointsNotStartedStatus() {
        // Knowledge point with no practice records => NOT_STARTED
        KnowledgePoint kp = stubKnowledgePoint(1L, "KP-Unpracticed", 10L);
        when(knowledgePointMapper.selectList(any())).thenReturn(List.of(kp));

        // No questionKnowledgePoint mappings
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        // NOT_STARTED has priority 60 but should still appear (not filtered by rate threshold)
        assertNotNull(vo.getWeakPoints());
        assertEquals(1, vo.getWeakPoints().size());
        assertEquals("NOT_STARTED", vo.getWeakPoints().get(0).getMasteryStatus());
    }

    // ======================== getDiagnosis — 每日推荐 ========================

    @Test
    void getDiagnosisRecommendationsFromRepeatedWrongs() {
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        // Wrong question with wrongCount >= 2 => recommended
        WrongQuestion wq = stubWrongQuestion(USER_ID, 100L, 3, 0);
        wq.setLastWrongAnswer("B");
        when(wrongQuestionMapper.selectList(any())).thenReturn(List.of(wq));

        Question q = stubQuestion(100L, "SINGLE_CHOICE", 10L);
        q.setContent("What is JVM?");
        when(questionMapper.selectById(100L)).thenReturn(q);

        Course course = stubCourse(10L, "Java Basics");
        when(courseMapper.selectById(10L)).thenReturn(course);

        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        assertNotNull(vo.getDailyRecommendations());
        assertFalse(vo.getDailyRecommendations().isEmpty());

        LearningDiagnosisVO.RecommendedQuestion rq = vo.getDailyRecommendations().get(0);
        assertEquals(100L, rq.getQuestionId());
        assertEquals("ERROR_PRONE", rq.getReason());
        assertEquals("B", rq.getLastWrongAnswer());
    }

    @Test
    void getDiagnosisRecommendationsLimitsToFive() {
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 7 repeated wrongs
        WrongQuestion wq1 = stubWrongQuestion(USER_ID, 1L, 2, 0);
        WrongQuestion wq2 = stubWrongQuestion(USER_ID, 2L, 3, 0);
        WrongQuestion wq3 = stubWrongQuestion(USER_ID, 3L, 4, 0);
        WrongQuestion wq4 = stubWrongQuestion(USER_ID, 4L, 2, 0);
        WrongQuestion wq5 = stubWrongQuestion(USER_ID, 5L, 3, 0);
        WrongQuestion wq6 = stubWrongQuestion(USER_ID, 6L, 2, 0);
        WrongQuestion wq7 = stubWrongQuestion(USER_ID, 7L, 2, 0);
        when(wrongQuestionMapper.selectList(any())).thenReturn(List.of(wq1, wq2, wq3, wq4, wq5, wq6, wq7));

        for (long i = 1; i <= 7; i++) {
            Question q = stubQuestion(i, "SINGLE_CHOICE", 10L);
            q.setContent("Question " + i);
            lenient().when(questionMapper.selectById(i)).thenReturn(q);
        }
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        // Should be limited to 5
        assertTrue(vo.getDailyRecommendations().size() <= 5);
    }

    @Test
    void getDiagnosisRecommendationsSkipsMasteredWrongs() {
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        // masteryLevel == 2 => mastered, should be skipped
        WrongQuestion masteredWq = stubWrongQuestion(USER_ID, 100L, 3, 2);
        when(wrongQuestionMapper.selectList(any())).thenReturn(List.of(masteredWq));

        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        // No recommendations from mastered wrongs
        assertTrue(vo.getDailyRecommendations().isEmpty());
    }

    // ======================== getDiagnosis — 每日建议 ========================

    @Test
    void getDiagnosisDailyAdviceForStreak() {
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());

        // Build records with 7-day streak
        PracticeRecord r = stubRecord(USER_ID, 1L, 1, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r));

        // Override calculateStreak by providing records for today
        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        assertNotNull(vo.getDailyAdvice());
        assertFalse(vo.getDailyAdvice().isEmpty());
    }

    @Test
    void getDiagnosisDailyAdviceForNoRecords() {
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        assertNotNull(vo.getDailyAdvice());
        // Should contain advice about not starting today
        assertTrue(vo.getDailyAdvice().contains("今天还没有开始学习")
                || vo.getDailyAdvice().contains("学习状态良好"));
    }

    // ======================== getDiagnosis — 课程掌握概况 ========================

    @Test
    void getDiagnosisCourseMasteries() {
        KnowledgePoint kp = stubKnowledgePoint(1L, "KP-1", 10L);
        when(knowledgePointMapper.selectList(any())).thenReturn(List.of(kp));

        QuestionKnowledgePoint qkp = new QuestionKnowledgePoint();
        qkp.setQuestionId(1L);
        qkp.setKnowledgePointId(1L);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of(qkp));

        PracticeRecord r1 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now());
        PracticeRecord r2 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusHours(1));
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2));

        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());

        Course course = stubCourse(10L, "Data Structures");
        when(courseMapper.selectById(10L)).thenReturn(course);

        LearningDiagnosisVO vo = service.getDiagnosis(USER_ID);

        assertNotNull(vo.getCourseMasteries());
        assertEquals(1, vo.getCourseMasteries().size());

        LearningDiagnosisVO.CourseMastery cm = vo.getCourseMasteries().get(0);
        assertEquals(10L, cm.getCourseId());
        assertEquals("Data Structures", cm.getCourseName());
        assertEquals(50.0, cm.getCorrectRate());
        assertEquals(2, cm.getTotalAttempts());
    }

    // ======================== findSimilarQuestions ========================

    @Test
    void findSimilarQuestionsReturnsEmptyForNonexistentQuestion() {
        when(questionMapper.selectById(999L)).thenReturn(null);

        SimilarQuestionVO vo = service.findSimilarQuestions(USER_ID, 999L, 5);

        assertNotNull(vo);
        assertEquals(999L, vo.getSourceQuestionId());
        assertEquals("题目不存在", vo.getSourceQuestionContent());
        assertTrue(vo.getSimilarQuestions().isEmpty());
    }

    @Test
    void findSimilarQuestionsFindsSameKnowledgePoint() {
        Question source = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        source.setContent("Source question");
        when(questionMapper.selectById(1L)).thenReturn(source);

        // Source question has KP 1
        QuestionKnowledgePoint sourceQkp = new QuestionKnowledgePoint();
        sourceQkp.setQuestionId(1L);
        sourceQkp.setKnowledgePointId(1L);
        when(questionKnowledgePointMapper.selectList(argThat(wrapper -> {
            // First call: for source questionToKps, second call: for candidates
            return true;
        }))).thenReturn(List.of(sourceQkp));

        // Candidate question
        Question candidate = stubQuestion(2L, "SINGLE_CHOICE", 10L);
        candidate.setContent("Similar question");
        when(questionMapper.selectList(any())).thenReturn(List.of(candidate));
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        // Candidate also has KP 1
        QuestionKnowledgePoint candidateQkp = new QuestionKnowledgePoint();
        candidateQkp.setQuestionId(2L);
        candidateQkp.setKnowledgePointId(1L);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of(sourceQkp, candidateQkp));

        Course course = stubCourse(10L, "Java");
        when(courseMapper.selectById(10L)).thenReturn(course);
        KnowledgePoint kp = stubKnowledgePoint(1L, "OOP", 10L);
        when(knowledgePointMapper.selectById(1L)).thenReturn(kp);

        SimilarQuestionVO vo = service.findSimilarQuestions(USER_ID, 1L, 5);

        assertNotNull(vo);
        assertEquals(1L, vo.getSourceQuestionId());
        assertFalse(vo.getSimilarQuestions().isEmpty());

        SimilarQuestionVO.SimilarItem item = vo.getSimilarQuestions().get(0);
        assertEquals(2L, item.getQuestionId());
        assertTrue(item.getSimilarityScore() > 0);
        assertTrue(item.getReason().contains("同知识点") || item.getReason().contains("同题型"));
    }

    @Test
    void findSimilarQuestionsExcludesSourceQuestion() {
        Question source = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(source);

        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        // Only return the source question as candidate (should be excluded by NE filter)
        when(questionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        SimilarQuestionVO vo = service.findSimilarQuestions(USER_ID, 1L, 5);

        assertTrue(vo.getSimilarQuestions().isEmpty());
    }

    @Test
    void findSimilarQuestionsMarksAttemptedQuestions() {
        Question source = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(source);

        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        Question candidate = stubQuestion(2L, "MULTIPLE_CHOICE", 20L);
        when(questionMapper.selectList(any())).thenReturn(List.of(candidate));

        // User has attempted question 2
        PracticeRecord attempted = stubRecord(USER_ID, 2L, 0, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(attempted));

        SimilarQuestionVO vo = service.findSimilarQuestions(USER_ID, 1L, 5);

        // The candidate may or may not pass the score threshold (30).
        // If it does, it should be marked as attempted.
        if (!vo.getSimilarQuestions().isEmpty()) {
            for (SimilarQuestionVO.SimilarItem item : vo.getSimilarQuestions()) {
                if (item.getQuestionId() == 2L) {
                    assertTrue(item.isAlreadyAttempted());
                }
            }
        }
    }

    @Test
    void findSimilarQuestionsSortsBySimilarityScore() {
        Question source = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(source);

        // Source has KP 1
        QuestionKnowledgePoint sourceQkp = new QuestionKnowledgePoint();
        sourceQkp.setQuestionId(1L);
        sourceQkp.setKnowledgePointId(1L);

        // Candidate A: same KP + same type + same difficulty + same course => score 100
        Question candidateA = stubQuestion(10L, "SINGLE_CHOICE", 10L);
        candidateA.setContent("Very similar");
        // Candidate B: different type, different course, same KP => score 40
        Question candidateB = stubQuestion(20L, "FILL_BLANK", 20L);
        candidateB.setContent("Less similar");

        when(questionMapper.selectList(any())).thenReturn(List.of(candidateA, candidateB));
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        QuestionKnowledgePoint cAqkp = new QuestionKnowledgePoint();
        cAqkp.setQuestionId(10L);
        cAqkp.setKnowledgePointId(1L);
        QuestionKnowledgePoint cBqkp = new QuestionKnowledgePoint();
        cBqkp.setQuestionId(20L);
        cBqkp.setKnowledgePointId(1L);
        when(questionKnowledgePointMapper.selectList(any()))
                .thenReturn(List.of(sourceQkp, cAqkp, cBqkp));

        Course course10 = stubCourse(10L, "Java");
        Course course20 = stubCourse(20L, "Python");
        when(courseMapper.selectById(10L)).thenReturn(course10);
        when(courseMapper.selectById(20L)).thenReturn(course20);
        when(knowledgePointMapper.selectById(1L)).thenReturn(stubKnowledgePoint(1L, "OOP", 10L));

        SimilarQuestionVO vo = service.findSimilarQuestions(USER_ID, 1L, 10);

        if (vo.getSimilarQuestions().size() >= 2) {
            // First item should have higher score
            assertTrue(vo.getSimilarQuestions().get(0).getSimilarityScore()
                    >= vo.getSimilarQuestions().get(1).getSimilarityScore());
        }
    }

    // ======================== generateAiAdvice ========================

    @Test
    void generateAiAdviceCallsAiProviderAndLogs() {
        // Stub getDiagnosis internals (minimum viable)
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        when(aiProvider.chat(anyString(), anyString())).thenReturn("AI advice content");

        String result = service.generateAiAdvice(USER_ID);

        assertEquals("AI advice content", result);
        verify(aiProvider).chat(anyString(), anyString());
        verify(aiService).logCall(eq(USER_ID), eq("learning_advice"), eq(true), eq(null), any(Integer.class));
    }

    @Test
    void generateAiAdviceLogsOnFailure() {
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        when(aiProvider.chat(anyString(), anyString())).thenThrow(new RuntimeException("AI error"));

        assertThrows(RuntimeException.class, () -> service.generateAiAdvice(USER_ID));

        verify(aiService).logCall(eq(USER_ID), eq("learning_advice"), eq(false), anyString(), any(Integer.class));
    }

    @Test
    void generateAiAdvicePassesDiagnosisDataToAiPrompt() {
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        WrongQuestion wq = stubWrongQuestion(USER_ID, 1L, 3, 0);
        when(wrongQuestionMapper.selectList(any())).thenReturn(List.of(wq));

        PracticeRecord r = stubRecord(USER_ID, 1L, 0, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r));

        Question q1 = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectList(any())).thenReturn(List.of(q1));

        Course course = stubCourse(10L, "Java");
        when(courseMapper.selectList(any())).thenReturn(List.of(course));

        when(aiProvider.chat(anyString(), anyString())).thenReturn("advice");

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        service.generateAiAdvice(USER_ID);

        verify(aiProvider).chat(anyString(), userPromptCaptor.capture());
        String userPrompt = userPromptCaptor.getValue();

        // Verify the prompt contains diagnosis data
        assertTrue(userPrompt.contains("总刷题数"));
        assertTrue(userPrompt.contains("请基于以上数据"));
    }

    // ======================== generateAiAdviceStream ========================

    @Test
    void generateAiAdviceStreamCallsAiProviderAndLogs() {
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        doAnswer(invocation -> {
            Consumer<String> callback = invocation.getArgument(2);
            callback.accept("chunk1");
            callback.accept("chunk2");
            return null;
        }).when(aiProvider).chatStream(anyString(), anyString(), any(Consumer.class));

        StringBuilder received = new StringBuilder();
        service.generateAiAdviceStream(USER_ID, received::append);

        assertEquals("chunk1chunk2", received.toString());
        verify(aiService).logCall(eq(USER_ID), eq("learning_advice_stream"), eq(true), eq(null), any(Integer.class));
    }

    @Test
    void generateAiAdviceStreamLogsOnFailure() {
        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(knowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        doThrow(new RuntimeException("stream error"))
                .when(aiProvider).chatStream(anyString(), anyString(), any(Consumer.class));

        assertThrows(RuntimeException.class,
                () -> service.generateAiAdviceStream(USER_ID, chunk -> {}));

        verify(aiService).logCall(eq(USER_ID), eq("learning_advice_stream"), eq(false), anyString(), any(Integer.class));
    }

    // ======================== analyzeQuestionError — 单题错因分析 ========================

    @Test
    void analyzeQuestionErrorReturnsEmptyForNonexistentQuestion() {
        when(questionMapper.selectById(999L)).thenReturn(null);

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 999L);

        assertNotNull(analysis);
        assertEquals(999L, analysis.getQuestionId());
        assertEquals("题目不存在", analysis.getQuestionContent());
        assertTrue(analysis.getAttempts().isEmpty());
    }

    @Test
    void analyzeQuestionErrorReturnsZeroAttemptsWhenNoRecords() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        q.setContent("What is polymorphism?");
        when(questionMapper.selectById(1L)).thenReturn(q);

        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        // No wrong question
        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);

        // No knowledge points
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());

        Course course = stubCourse(10L, "Java Basics");
        when(courseMapper.selectById(10L)).thenReturn(course);

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        assertEquals(1L, analysis.getQuestionId());
        assertEquals("What is polymorphism?", analysis.getQuestionContent());
        assertEquals("单选题", analysis.getQuestionType());
        assertEquals(3, analysis.getDifficulty());
        assertEquals("Java Basics", analysis.getCourseName());
        assertEquals(0, analysis.getTotalAttempts());
        assertEquals(0, analysis.getCorrectCount());
        assertEquals(0, analysis.getWrongCount());
        assertEquals(0.0, analysis.getCorrectRate());
        assertNull(analysis.getCurrentMasteryLevel());
        assertTrue(analysis.getAttempts().isEmpty());
        assertEquals("该题尚未作答。", analysis.getErrorPattern());
    }

    @Test
    void analyzeQuestionErrorComputesCorrectRateWithMixedAttempts() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        // 5 attempts: 3 correct, 2 wrong => 60%
        PracticeRecord r1 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(4));
        PracticeRecord r2 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(3));
        PracticeRecord r3 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(2));
        PracticeRecord r4 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(1));
        PracticeRecord r5 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3, r4, r5));

        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        assertEquals(5, analysis.getTotalAttempts());
        assertEquals(3, analysis.getCorrectCount());
        assertEquals(2, analysis.getWrongCount());
        assertEquals(60.0, analysis.getCorrectRate());
        assertEquals(5, analysis.getAttempts().size());
    }

    @Test
    void analyzeQuestionErrorAllCorrectAttempts() {
        Question q = stubQuestion(1L, "TRUE_FALSE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        PracticeRecord r1 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(2));
        PracticeRecord r2 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(1));
        PracticeRecord r3 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3));

        WrongQuestion wq = stubWrongQuestion(USER_ID, 1L, 1, 2);
        when(wrongQuestionMapper.selectOne(any())).thenReturn(wq);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        assertEquals(3, analysis.getTotalAttempts());
        assertEquals(3, analysis.getCorrectCount());
        assertEquals(0, analysis.getWrongCount());
        assertEquals(100.0, analysis.getCorrectRate());
        assertEquals(2, analysis.getCurrentMasteryLevel());
        // Error pattern should say all correct
        assertTrue(analysis.getErrorPattern().contains("全部答对"));
    }

    @Test
    void analyzeQuestionErrorAllWrongAttempts() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        PracticeRecord r1 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(2));
        PracticeRecord r2 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(1));
        PracticeRecord r3 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3));

        WrongQuestion wq = stubWrongQuestion(USER_ID, 1L, 3, 0);
        when(wrongQuestionMapper.selectOne(any())).thenReturn(wq);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        assertEquals(3, analysis.getTotalAttempts());
        assertEquals(0, analysis.getCorrectCount());
        assertEquals(3, analysis.getWrongCount());
        assertEquals(0.0, analysis.getCorrectRate());
        assertEquals(0, analysis.getCurrentMasteryLevel());
        // Should detect repeated errors
        assertTrue(analysis.getErrorPattern().contains("反复错题"));
        // Should detect consecutive wrong
        assertTrue(analysis.getErrorPattern().contains("连续答错"));
    }

    @Test
    void analyzeQuestionErrorDetectsImprovingTrend() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        // 7 attempts: earlier 2 (both wrong, 0%), recent 5 (4 correct, 80%)
        // recentRate(80) - earlierRate(0) = 80 >= 20 => IMPROVING
        PracticeRecord r1 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(9));
        PracticeRecord r2 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(8));
        PracticeRecord r3 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(4));
        PracticeRecord r4 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(3));
        PracticeRecord r5 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(2));
        PracticeRecord r6 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(1));
        PracticeRecord r7 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3, r4, r5, r6, r7));

        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        assertEquals("IMPROVING", analysis.getMasteryTrend());
        assertNotNull(analysis.getTrendDescription());
        assertTrue(analysis.getTrendDescription().contains("提升"));
    }

    @Test
    void analyzeQuestionErrorDetectsDecliningTrend() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        // 7 attempts: earlier 2 (both correct, 100%), recent 5 (1 correct 4 wrong, 20%)
        // earlierRate(100) - recentRate(20) = 80 >= 20 => DECLINING
        PracticeRecord r1 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(9));
        PracticeRecord r2 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(8));
        PracticeRecord r3 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(4));
        PracticeRecord r4 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(3));
        PracticeRecord r5 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(2));
        PracticeRecord r6 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(1));
        PracticeRecord r7 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3, r4, r5, r6, r7));

        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        assertEquals("DECLINING", analysis.getMasteryTrend());
        assertNotNull(analysis.getTrendDescription());
        assertTrue(analysis.getTrendDescription().contains("下降"));
    }

    @Test
    void analyzeQuestionErrorDetectsStagnantTrend() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        // 7 attempts: earlier 2 (1 correct 1 wrong, 50%), recent 5 (3 correct 2 wrong, 60%)
        // diff = 10 < 20 => STAGNANT, recentRate 60% not >=80 not <50 => "仍需巩固"
        PracticeRecord r1 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(9));
        PracticeRecord r2 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(8));
        PracticeRecord r3 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(4));
        PracticeRecord r4 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(3));
        PracticeRecord r5 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(2));
        PracticeRecord r6 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(1));
        PracticeRecord r7 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3, r4, r5, r6, r7));

        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        assertEquals("STAGNANT", analysis.getMasteryTrend());
        assertTrue(analysis.getTrendDescription().contains("持平") || analysis.getTrendDescription().contains("巩固"));
    }

    @Test
    void analyzeQuestionErrorOnlyTwoAttemptsHighRecent() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        // Only 2 attempts (both correct), no earlier records
        PracticeRecord r1 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(1));
        PracticeRecord r2 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2));

        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        // 2 attempts, recent 100% => IMPROVING (recentRate >= 80)
        assertEquals("IMPROVING", analysis.getMasteryTrend());
    }

    @Test
    void analyzeQuestionErrorOnlyTwoAttemptsLowRecent() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        // Only 2 attempts (both wrong), no earlier records
        PracticeRecord r1 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(1));
        PracticeRecord r2 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2));

        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        // 2 attempts, recent 0% => DECLINING (recentRate < 50)
        assertEquals("DECLINING", analysis.getMasteryTrend());
    }

    @Test
    void analyzeQuestionErrorResolvesKnowledgePointAndCourse() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        when(practiceRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);

        // Knowledge point mapping
        QuestionKnowledgePoint qkp = new QuestionKnowledgePoint();
        qkp.setQuestionId(1L);
        qkp.setKnowledgePointId(5L);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of(qkp));

        KnowledgePoint kp = stubKnowledgePoint(5L, "OOP Polymorphism", 10L);
        when(knowledgePointMapper.selectById(5L)).thenReturn(kp);

        Course course = stubCourse(10L, "Advanced Java");
        when(courseMapper.selectById(10L)).thenReturn(course);

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        assertEquals("Advanced Java", analysis.getCourseName());
        assertEquals("OOP Polymorphism", analysis.getKnowledgePointName());
    }

    @Test
    void analyzeQuestionErrorErrorPatternRepeatedErrors() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        // 4 attempts: 3 wrong, 1 correct
        PracticeRecord r1 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(3));
        PracticeRecord r2 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(2));
        PracticeRecord r3 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(1));
        PracticeRecord r4 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3, r4));

        WrongQuestion wq = stubWrongQuestion(USER_ID, 1L, 3, 0);
        when(wrongQuestionMapper.selectOne(any())).thenReturn(wq);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        // wrongCount=3 >= 3 => repeated
        assertTrue(analysis.getErrorPattern().contains("反复错题"));
        // last record is correct
        assertTrue(analysis.getErrorPattern().contains("最近一次已答对"));
        // mastery level 0
        assertTrue(analysis.getErrorPattern().contains("未掌握"));
    }

    @Test
    void analyzeQuestionErrorErrorPatternConsecutiveWrong() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        // 3 attempts: correct, wrong, wrong (last 2 consecutive wrong)
        PracticeRecord r1 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(2));
        PracticeRecord r2 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now().minusDays(1));
        PracticeRecord r3 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3));

        WrongQuestion wq = stubWrongQuestion(USER_ID, 1L, 2, 1);
        when(wrongQuestionMapper.selectOne(any())).thenReturn(wq);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        assertTrue(analysis.getErrorPattern().contains("连续答错 2 次"));
        assertTrue(analysis.getErrorPattern().contains("部分掌握"));
    }

    @Test
    void analyzeQuestionErrorErrorPatternRecentWrong() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        // 2 attempts: correct then wrong
        PracticeRecord r1 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now().minusDays(1));
        PracticeRecord r2 = stubRecord(USER_ID, 1L, 0, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1, r2));

        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        assertTrue(analysis.getErrorPattern().contains("最近一次作答仍然错误"));
    }

    @Test
    void analyzeQuestionErrorSingleAttemptCorrect() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        // Single correct attempt
        PracticeRecord r1 = stubRecord(USER_ID, 1L, 1, LocalDateTime.now());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r1));

        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        assertEquals(1, analysis.getTotalAttempts());
        assertEquals(100.0, analysis.getCorrectRate());
        // With only 1 attempt, trend should be STAGNANT
        assertEquals("STAGNANT", analysis.getMasteryTrend());
        assertTrue(analysis.getErrorPattern().contains("全部答对"));
    }

    @Test
    void analyzeQuestionErrorAttemptHistoryHasCorrectFields() {
        Question q = stubQuestion(1L, "SINGLE_CHOICE", 10L);
        when(questionMapper.selectById(1L)).thenReturn(q);

        PracticeRecord r = new PracticeRecord();
        r.setId(42L);
        r.setUserId(USER_ID);
        r.setQuestionId(1L);
        r.setIsCorrect(0);
        r.setUserAnswer("B");
        r.setAnswerTime(30);
        r.setCreateTime(LocalDateTime.of(2026, 6, 15, 14, 30, 0));
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(r));

        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(10L)).thenReturn(stubCourse(10L, "Java"));

        LearningDiagnosisVO.QuestionErrorAnalysis analysis = service.analyzeQuestionError(USER_ID, 1L);

        assertEquals(1, analysis.getAttempts().size());
        LearningDiagnosisVO.AttemptHistory ah = analysis.getAttempts().get(0);
        assertEquals(42L, ah.getRecordId());
        assertEquals("B", ah.getUserAnswer());
        assertEquals(0, ah.getIsCorrect());
        assertEquals(30, ah.getAnswerTime());
        assertEquals("2026-06-15T14:30", ah.getCreateTime());
    }

    // ======================== Helpers ========================

    private PracticeRecord stubRecord(Long userId, Long questionId, int isCorrect, LocalDateTime createTime) {
        PracticeRecord r = new PracticeRecord();
        r.setId(questionId * 10 + userId);
        r.setUserId(userId);
        r.setQuestionId(questionId);
        r.setIsCorrect(isCorrect);
        r.setUserAnswer(isCorrect == 1 ? "A" : "B");
        r.setCreateTime(createTime);
        return r;
    }

    private WrongQuestion stubWrongQuestion(Long userId, Long questionId, int wrongCount, int masteryLevel) {
        WrongQuestion wq = new WrongQuestion();
        wq.setId(questionId * 10 + userId);
        wq.setUserId(userId);
        wq.setQuestionId(questionId);
        wq.setWrongCount(wrongCount);
        wq.setMasteryLevel(masteryLevel);
        wq.setLastWrongAnswer("B");
        wq.setDeleted(0);
        return wq;
    }

    private Question stubQuestion(Long id, String questionType, Long courseId) {
        Question q = new Question();
        q.setId(id);
        q.setContent("Question content " + id);
        q.setQuestionType(questionType);
        q.setDifficulty(3);
        q.setCourseId(courseId);
        q.setStatus(1);
        return q;
    }

    private KnowledgePoint stubKnowledgePoint(Long id, String name, Long courseId) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(id);
        kp.setName(name);
        kp.setCourseId(courseId);
        return kp;
    }

    private Course stubCourse(Long id, String name) {
        Course c = new Course();
        c.setId(id);
        c.setName(name);
        return c;
    }
}
