package com.learnplatform.service;

import com.learnplatform.dto.AdminStatisticsVO;
import com.learnplatform.dto.LearningReportVO;
import com.learnplatform.dto.StatisticsVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.ExamRecord;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.User;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import com.learnplatform.IntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统计服务集成测试 —— 真实 MySQL + Flyway 迁移
 * 验证用户统计、每日趋势、课程统计、管理端概览、学习报告等核心聚合逻辑。
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
class StatisticsServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private LearningReportService learningReportService;

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private QuestionOptionMapper questionOptionMapper;
    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private KnowledgePointMapper knowledgePointMapper;
    @Autowired
    private PracticeRecordMapper practiceRecordMapper;
    @Autowired
    private WrongQuestionMapper wrongQuestionMapper;
    @Autowired
    private ExamPaperMapper examPaperMapper;
    @Autowired
    private ExamRecordMapper examRecordMapper;

    private static Long userId;
    private static Long otherUserId;
    private static Long courseId1;
    private static Long courseId2;
    private static Long questionId1;
    private static Long questionId2;
    private static Long questionId3;

    @BeforeAll
    static void setupTestData(
            @Autowired UserMapper userMapper,
            @Autowired CourseMapper courseMapper,
            @Autowired QuestionMapper questionMapper,
            @Autowired QuestionOptionMapper questionOptionMapper,
            @Autowired KnowledgePointMapper knowledgePointMapper,
            @Autowired PracticeRecordMapper practiceRecordMapper,
            @Autowired WrongQuestionMapper wrongQuestionMapper,
            @Autowired ExamRecordMapper examRecordMapper
    ) {
        // 1. 创建用户
        User user = new User();
        user.setUsername("stats_integration_user");
        user.setPassword("$2a$10$dummyHashForTest");
        user.setNickname("统计测试用户");
        user.setRole("USER");
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);
        userId = user.getId();

        User otherUser = new User();
        otherUser.setUsername("stats_other_user");
        otherUser.setPassword("$2a$10$dummyHashForTest");
        otherUser.setNickname("其他用户");
        otherUser.setRole("USER");
        otherUser.setStatus(1);
        otherUser.setDeleted(0);
        userMapper.insert(otherUser);
        otherUserId = otherUser.getId();

        // 2. 创建课程
        Course course1 = new Course();
        course1.setName("Java 基础");
        course1.setDescription("Java 基础课程");
        course1.setSortOrder(1);
        course1.setDeleted(0);
        courseMapper.insert(course1);
        courseId1 = course1.getId();

        Course course2 = new Course();
        course2.setName("数据结构");
        course2.setDescription("数据结构课程");
        course2.setSortOrder(2);
        course2.setDeleted(0);
        courseMapper.insert(course2);
        courseId2 = course2.getId();

        // 3. 创建题目
        Question q1 = new Question();
        q1.setContent("Java 变量声明：以下哪个是正确的变量声明？");
        q1.setQuestionType("SINGLE_CHOICE");
        q1.setDifficulty(1);
        q1.setCourseId(courseId1);
        q1.setStatus(1);
        q1.setDeleted(0);
        questionMapper.insert(q1);
        questionId1 = q1.getId();

        QuestionOption opt1 = new QuestionOption();
        opt1.setQuestionId(questionId1);
        opt1.setOptionLabel("A");
        opt1.setContent("int a = 1;");
        opt1.setIsCorrect(1);
        opt1.setSortOrder(1);
        questionOptionMapper.insert(opt1);

        Question q2 = new Question();
        q2.setContent("Java 循环语句：以下哪个用于循环？");
        q2.setQuestionType("SINGLE_CHOICE");
        q2.setDifficulty(2);
        q2.setCourseId(courseId1);
        q2.setStatus(1);
        q2.setDeleted(0);
        questionMapper.insert(q2);
        questionId2 = q2.getId();

        QuestionOption opt2 = new QuestionOption();
        opt2.setQuestionId(questionId2);
        opt2.setOptionLabel("A");
        opt2.setContent("for");
        opt2.setIsCorrect(1);
        opt2.setSortOrder(1);
        questionOptionMapper.insert(opt2);

        Question q3 = new Question();
        q3.setContent("数组排序：以下哪个排序算法平均复杂度最低？");
        q3.setQuestionType("SINGLE_CHOICE");
        q3.setDifficulty(3);
        q3.setCourseId(courseId2);
        q3.setStatus(1);
        q3.setDeleted(0);
        questionMapper.insert(q3);
        questionId3 = q3.getId();

        QuestionOption opt3 = new QuestionOption();
        opt3.setQuestionId(questionId3);
        opt3.setOptionLabel("A");
        opt3.setContent("快速排序");
        opt3.setIsCorrect(1);
        opt3.setSortOrder(1);
        questionOptionMapper.insert(opt3);

        // 4. 创建练习记录：3 道题（2 正确 + 1 错误）
        LocalDateTime now = LocalDateTime.now();
        PracticeRecord r1 = new PracticeRecord();
        r1.setUserId(userId);
        r1.setQuestionId(questionId1);
        r1.setUserAnswer("int a = 1;");
        r1.setIsCorrect(1);
        r1.setAnswerTime(10);
        r1.setCreateTime(now);
        practiceRecordMapper.insert(r1);

        PracticeRecord r2 = new PracticeRecord();
        r2.setUserId(userId);
        r2.setQuestionId(questionId2);
        r2.setUserAnswer("while");
        r2.setIsCorrect(0);
        r2.setAnswerTime(15);
        r2.setCreateTime(now);
        practiceRecordMapper.insert(r2);

        PracticeRecord r3 = new PracticeRecord();
        r3.setUserId(userId);
        r3.setQuestionId(questionId3);
        r3.setUserAnswer("快速排序");
        r3.setIsCorrect(1);
        r3.setAnswerTime(20);
        r3.setCreateTime(now);
        practiceRecordMapper.insert(r3);

        // 其他用户的记录（不应计入统计）
        PracticeRecord rOther = new PracticeRecord();
        rOther.setUserId(otherUserId);
        rOther.setQuestionId(questionId1);
        rOther.setUserAnswer("int a = 1;");
        rOther.setIsCorrect(1);
        rOther.setAnswerTime(5);
        rOther.setCreateTime(now);
        practiceRecordMapper.insert(rOther);

        // 5. 创建错题本记录
        WrongQuestion wq = new WrongQuestion();
        wq.setUserId(userId);
        wq.setQuestionId(questionId2);
        wq.setWrongCount(2);
        wq.setMasteryLevel(0);
        wq.setDeleted(0);
        wq.setCreateTime(now);
        wrongQuestionMapper.insert(wq);

        // 创建一个已掌握的错题
        WrongQuestion wqMastered = new WrongQuestion();
        wqMastered.setUserId(userId);
        wqMastered.setQuestionId(questionId1);
        wqMastered.setWrongCount(1);
        wqMastered.setMasteryLevel(2);
        wqMastered.setDeleted(0);
        wqMastered.setCreateTime(now);
        wrongQuestionMapper.insert(wqMastered);

        // 6. 创建考试记录
        ExamRecord examRecord = new ExamRecord();
        examRecord.setUserId(userId);
        examRecord.setExamPaperId(0L); // 简化，不关联真实试卷
        examRecord.setStartTime(now.minusMinutes(30));
        examRecord.setEndTime(now);
        examRecord.setScore(85);
        examRecord.setTotalScore(100);
        examRecord.setStatus(1);
        examRecord.setCreateTime(now);
        examRecordMapper.insert(examRecord);
    }

    // ======================== getUserStatistics ========================

    @Test
    @Order(1)
    void getUserStatistics_withPracticeRecords_returnsCorrectOverview() {
        StatisticsVO vo = statisticsService.getUserStatistics(userId);

        assertEquals(3, vo.getTotalPractice());
        assertEquals(2, vo.getCorrectCount());
        assertEquals(1, vo.getWrongCount());
        // correctRate = 2/3 * 100 = 66.7%
        assertEquals(66.7, vo.getCorrectRate(), 0.1);
        // 今日刷题：3 条记录都在今天
        assertTrue(vo.getTodayPractice() >= 3);
        // 连续天数至少为 1（今天有记录）
        assertTrue(vo.getStreakDays() >= 1);
        // 错题本：2 条
        assertEquals(2, vo.getWrongQuestionCount());
        // 已掌握：1 条
        assertEquals(1, vo.getMasteredCount());
    }

    @Test
    @Order(2)
    void getUserStatistics_emptyUser_returnsZeros() {
        StatisticsVO vo = statisticsService.getUserStatistics(otherUserId);

        assertEquals(1, vo.getTotalPractice());
        assertEquals(1, vo.getCorrectCount());
        assertEquals(0, vo.getWrongCount());
        assertEquals(0, vo.getWrongQuestionCount());
        assertEquals(0, vo.getMasteredCount());
    }

    @Test
    @Order(3)
    void getUserStatistics_nonExistentUser_returnsZeros() {
        StatisticsVO vo = statisticsService.getUserStatistics(999999L);

        assertEquals(0, vo.getTotalPractice());
        assertEquals(0, vo.getCorrectCount());
        assertEquals(0, vo.getWrongCount());
        assertEquals(0.0, vo.getCorrectRate());
    }

    // ======================== getDailyTrend ========================

    @Test
    @Order(4)
    void getDailyTrend_returns7DaysWithData() {
        List<Map<String, Object>> trend = statisticsService.getDailyTrend(userId);

        assertEquals(7, trend.size());
        // 今天应该有数据
        Map<String, Object> todayItem = trend.get(6); // 最后一个是今天
        assertNotNull(todayItem.get("date"));
        assertTrue(numberValue(todayItem.get("total")) >= 3);
        assertEquals(2, numberValue(todayItem.get("correct")));
        assertTrue(numberValue(todayItem.get("wrong")) >= 1);
    }

    @Test
    @Order(5)
    void getDailyTrend_emptyUser_returns7DaysWithZeros() {
        List<Map<String, Object>> trend = statisticsService.getDailyTrend(999999L);

        assertEquals(7, trend.size());
        for (Map<String, Object> day : trend) {
            assertEquals(0, numberValue(day.get("total")));
            assertEquals(0, numberValue(day.get("correct")));
            assertEquals(0, numberValue(day.get("wrong")));
        }
    }

    // ======================== getCourseStats ========================

    @Test
    @Order(6)
    void getCourseStats_groupsByCourseCorrectly() {
        List<Map<String, Object>> courseStats = statisticsService.getCourseStats(userId);

        // 2 门课程
        assertEquals(2, courseStats.size());
        // 按 total 降序，course1 有 2 条，course2 有 1 条
        Map<String, Object> first = courseStats.get(0);
        assertEquals(courseId1, first.get("courseId"));
        assertEquals(2, numberValue(first.get("total")));
        assertEquals(1, numberValue(first.get("correct")));
        // correctRate = 1/2 * 100 = 50.0
        assertEquals(50.0, (double) first.get("correctRate"), 0.1);

        Map<String, Object> second = courseStats.get(1);
        assertEquals(courseId2, second.get("courseId"));
        assertEquals(1, numberValue(second.get("total")));
        assertEquals(1, numberValue(second.get("correct")));
        assertEquals(100.0, (double) second.get("correctRate"), 0.1);
    }

    @Test
    @Order(7)
    void getCourseStats_emptyUser_returnsEmptyList() {
        List<Map<String, Object>> courseStats = statisticsService.getCourseStats(999999L);
        assertTrue(courseStats.isEmpty());
    }

    // ======================== getAdminStatistics ========================

    @Test
    @Order(8)
    void getAdminStatistics_returnsPlatformOverview() {
        AdminStatisticsVO vo = statisticsService.getAdminStatistics();

        // 至少有我们创建的 2 个用户
        assertTrue(vo.getTotalUsers() >= 2);
        assertTrue(vo.getEnabledUsers() >= 2);
        // 至少有 3 道题目
        assertTrue(vo.getTotalQuestions() >= 3);
        // 至少有 4 条练习记录（3 + 1 other user）
        assertTrue(vo.getTotalPracticeRecords() >= 4);

        // 题型分布不为空
        assertNotNull(vo.getQuestionTypeDistribution());
        assertTrue(vo.getQuestionTypeDistribution().containsKey("单选题"));
        assertTrue(vo.getQuestionTypeDistribution().get("单选题") >= 3);

        // 每日活跃数据 7 天
        assertNotNull(vo.getDailyActivity());
        assertEquals(7, vo.getDailyActivity().size());
        // 今天至少有 2 个活跃用户
        AdminStatisticsVO.DailyActivity todayActivity = vo.getDailyActivity().get(6);
        assertTrue(todayActivity.getActiveUsers() >= 2);
        assertTrue(todayActivity.getPracticeCount() >= 4);
    }

    // ======================== getLearningReport ========================

    @Test
    @Order(9)
    void getLearningReport_returnsMonthlyReport() {
        LearningReportVO vo = learningReportService.getLearningReport(userId);

        // 本月刷题量
        assertEquals(3, vo.getMonthTotalPractice());
        assertEquals(2, vo.getMonthCorrectCount());
        // 正确率 = 2/3 * 100 = 66.7
        assertEquals(66.7, vo.getMonthCorrectRate(), 0.1);

        // 上月刷题量（我们没创建上月数据）
        assertEquals(0, vo.getLastMonthTotalPractice());
        assertEquals(0.0, vo.getLastMonthCorrectRate());
        // 刷题量环比：上月 0，本月 3，增长 100%
        assertEquals(100.0, vo.getPracticeGrowthRate(), 0.1);
        assertEquals(66.7, vo.getCorrectRateChange(), 0.1);
        assertEquals(1, vo.getActiveStudyDays());

        // 本月错题新增
        assertTrue(vo.getMonthNewWrongCount() >= 1);
        // 已掌握错题数
        assertTrue(vo.getMonthMasteredCount() >= 1);
        assertTrue(vo.getWrongQuestionConversionRate() > 0);

        // 本月考试
        assertEquals(1, vo.getMonthExamCount());
        assertEquals(85.0, vo.getMonthExamAvgScore(), 0.1);

        // 每日趋势
        assertNotNull(vo.getDailyTrend());
        assertTrue(vo.getDailyTrend().size() >= 1);

        // 课程统计
        assertNotNull(vo.getCourseStats());
        assertEquals(2, vo.getCourseStats().size());

        // 题型分布
        assertNotNull(vo.getQuestionTypeDistribution());
        assertTrue(vo.getQuestionTypeDistribution().containsKey("单选题"));
        assertEquals(3, vo.getQuestionTypeDistribution().get("单选题"));

        // 学习效果指标
        assertNotNull(vo.getLearningEffectScore());
        assertTrue(vo.getLearningEffectScore() > 0);
        assertNotNull(vo.getLearningEffectLevel());
        assertNotNull(vo.getLearningEffectLabel());
        assertNotNull(vo.getLearningEffectSummary());
    }

    @Test
    @Order(10)
    void getLearningReport_emptyUser_returnsZeros() {
        LearningReportVO vo = learningReportService.getLearningReport(999999L);

        assertEquals(0, vo.getMonthTotalPractice());
        assertEquals(0, vo.getMonthCorrectCount());
        assertEquals(0.0, vo.getMonthCorrectRate());
        assertEquals(0, vo.getLastMonthTotalPractice());
        assertEquals(0, vo.getMonthNewWrongCount());
        assertEquals(0, vo.getMonthMasteredCount());
        assertEquals(0, vo.getMonthExamCount());
        assertEquals(0.0, vo.getMonthExamAvgScore());
        assertEquals(0.0, vo.getCorrectRateChange());
        assertEquals(0.0, vo.getWrongQuestionConversionRate());
        assertEquals(0.0, vo.getReviewMasteryRate());
        assertEquals(0, vo.getActiveStudyDays());
        assertEquals("AT_RISK", vo.getLearningEffectLevel());
        assertNotNull(vo.getDailyTrend());
        assertNotNull(vo.getCourseStats());
        assertTrue(vo.getCourseStats().isEmpty());
    }

    private static long numberValue(Object value) {
        assertTrue(value instanceof Number, "value should be numeric");
        return ((Number) value).longValue();
    }
}
