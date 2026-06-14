package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.PracticeRecordVO;
import com.learnplatform.dto.PracticeResultVO;
import com.learnplatform.dto.PracticeSubmitRequest;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import com.learnplatform.IntegrationTestBase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 刷题服务集成测试 —— 真实 MySQL + Flyway 迁移
 * 验证刷题提交、判分、错题归集、记录查询等核心流程。
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
class PracticeServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PracticeService practiceService;

    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private QuestionOptionMapper questionOptionMapper;
    @Autowired
    private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Autowired
    private PracticeRecordMapper practiceRecordMapper;
    @Autowired
    private WrongQuestionMapper wrongQuestionMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private KnowledgePointMapper knowledgePointMapper;
    @Autowired
    private UserFavoriteQuestionMapper userFavoriteQuestionMapper;

    private static Long userId;
    private static Long singleChoiceQId;   // 单选题
    private static Long multiChoiceQId;    // 多选题
    private static Long fillBlankQId;      // 填空题
    private static Long trueFalseQId;      // 判断题
    private static Long courseId;
    private static Long knowledgePointId;

    @BeforeAll
    static void setupTestData(
            @Autowired UserMapper userMapper,
            @Autowired QuestionMapper questionMapper,
            @Autowired QuestionOptionMapper questionOptionMapper,
            @Autowired CourseMapper courseMapper,
            @Autowired KnowledgePointMapper knowledgePointMapper,
            @Autowired QuestionKnowledgePointMapper questionKnowledgePointMapper
    ) {
        // 1. 创建用户
        User user = new User();
        user.setUsername("practice_integration_user");
        user.setPassword("$2a$10$dummyHashForTest");
        user.setNickname("刷题测试用户");
        user.setRole("USER");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);
        userId = user.getId();

        // 2. 创建课程
        Course course = new Course();
        course.setName("集成测试课程");
        course.setDeleted(0);
        course.setCreateTime(LocalDateTime.now());
        course.setUpdateTime(LocalDateTime.now());
        courseMapper.insert(course);
        courseId = course.getId();

        // 3. 创建知识点
        KnowledgePoint kp = new KnowledgePoint();
        kp.setName("集成测试知识点");
        kp.setCourseId(courseId);
        kp.setDeleted(0);
        kp.setCreateTime(LocalDateTime.now());
        kp.setUpdateTime(LocalDateTime.now());
        knowledgePointMapper.insert(kp);
        knowledgePointId = kp.getId();

        // 4. 创建单选题 (SINGLE_CHOICE)
        Question q1 = new Question();
        q1.setContent("太阳系中最大的行星是?");
        q1.setQuestionType("SINGLE_CHOICE");
        q1.setDifficulty(1);
        q1.setAnalysis("木星是太阳系最大的行星");
        q1.setCourseId(courseId);
        q1.setStatus(1);
        q1.setDeleted(0);
        q1.setCreateTime(LocalDateTime.now());
        q1.setUpdateTime(LocalDateTime.now());
        questionMapper.insert(q1);
        singleChoiceQId = q1.getId();

        QuestionOption opt1a = new QuestionOption();
        opt1a.setQuestionId(singleChoiceQId);
        opt1a.setContent("地球");
        opt1a.setIsCorrect(0);
        opt1a.setSortOrder(1);
        questionOptionMapper.insert(opt1a);

        QuestionOption opt1b = new QuestionOption();
        opt1b.setQuestionId(singleChoiceQId);
        opt1b.setContent("木星");
        opt1b.setIsCorrect(1);
        opt1b.setSortOrder(2);
        questionOptionMapper.insert(opt1b);

        QuestionOption opt1c = new QuestionOption();
        opt1c.setQuestionId(singleChoiceQId);
        opt1c.setContent("土星");
        opt1c.setIsCorrect(0);
        opt1c.setSortOrder(3);
        questionOptionMapper.insert(opt1c);

        // 5. 创建多选题 (MULTIPLE_CHOICE)
        Question q2 = new Question();
        q2.setContent("以下哪些是编程语言?");
        q2.setQuestionType("MULTIPLE_CHOICE");
        q2.setDifficulty(2);
        q2.setAnalysis("Java 和 Python 是编程语言");
        q2.setCourseId(courseId);
        q2.setStatus(1);
        q2.setDeleted(0);
        q2.setCreateTime(LocalDateTime.now());
        q2.setUpdateTime(LocalDateTime.now());
        questionMapper.insert(q2);
        multiChoiceQId = q2.getId();

        QuestionOption opt2a = new QuestionOption();
        opt2a.setQuestionId(multiChoiceQId);
        opt2a.setContent("Java");
        opt2a.setIsCorrect(1);
        opt2a.setSortOrder(1);
        questionOptionMapper.insert(opt2a);

        QuestionOption opt2b = new QuestionOption();
        opt2b.setQuestionId(multiChoiceQId);
        opt2b.setContent("HTML");
        opt2b.setIsCorrect(0);
        opt2b.setSortOrder(2);
        questionOptionMapper.insert(opt2b);

        QuestionOption opt2c = new QuestionOption();
        opt2c.setQuestionId(multiChoiceQId);
        opt2c.setContent("Python");
        opt2c.setIsCorrect(1);
        opt2c.setSortOrder(3);
        questionOptionMapper.insert(opt2c);

        // 6. 创建填空题 (FILL_BLANK)
        Question q3 = new Question();
        q3.setContent("CPU 的全称是___");
        q3.setQuestionType("FILL_BLANK");
        q3.setDifficulty(2);
        q3.setAnalysis("CPU = Central Processing Unit");
        q3.setCourseId(courseId);
        q3.setStatus(1);
        q3.setDeleted(0);
        q3.setCreateTime(LocalDateTime.now());
        q3.setUpdateTime(LocalDateTime.now());
        questionMapper.insert(q3);
        fillBlankQId = q3.getId();

        // 7. 创建判断题 (TRUE_FALSE)
        Question q4 = new Question();
        q4.setContent("地球是平的");
        q4.setQuestionType("TRUE_FALSE");
        q4.setDifficulty(1);
        q4.setAnalysis("地球是椭球体");
        q4.setCourseId(courseId);
        q4.setStatus(1);
        q4.setDeleted(0);
        q4.setCreateTime(LocalDateTime.now());
        q4.setUpdateTime(LocalDateTime.now());
        questionMapper.insert(q4);
        trueFalseQId = q4.getId();

        QuestionOption opt4a = new QuestionOption();
        opt4a.setQuestionId(trueFalseQId);
        opt4a.setContent("正确");
        opt4a.setIsCorrect(0);
        opt4a.setSortOrder(1);
        questionOptionMapper.insert(opt4a);

        QuestionOption opt4b = new QuestionOption();
        opt4b.setQuestionId(trueFalseQId);
        opt4b.setContent("错误");
        opt4b.setIsCorrect(1);
        opt4b.setSortOrder(2);
        questionOptionMapper.insert(opt4b);

        // 8. 关联题目到知识点
        QuestionKnowledgePoint qkp = new QuestionKnowledgePoint();
        qkp.setQuestionId(singleChoiceQId);
        qkp.setKnowledgePointId(knowledgePointId);
        questionKnowledgePointMapper.insert(qkp);
    }

    // ======================== 提交答案集成测试 ========================

    @Test
    @Order(1)
    @DisplayName("提交答案：单选题答对，保存记录并判为正确")
    void submitAnswer_singleChoiceCorrect_savesRecordAndMarkCorrect() {
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setQuestionId(singleChoiceQId);
        request.setUserAnswer("B");
        request.setAnswerTime(15);

        PracticeResultVO result = practiceService.submitAnswer(request, userId);

        assertNotNull(result.getRecordId());
        assertEquals(singleChoiceQId, result.getQuestionId());
        assertTrue(result.getCorrect());
        assertEquals("B", result.getCorrectAnswer());
        assertNotNull(result.getAnalysis());

        // 验证数据库中记录已保存
        PracticeRecord record = practiceRecordMapper.selectById(result.getRecordId());
        assertNotNull(record);
        assertEquals(userId, record.getUserId());
        assertEquals(singleChoiceQId, record.getQuestionId());
        assertEquals(1, record.getIsCorrect());
        assertEquals(15, record.getAnswerTime());
    }

    @Test
    @Order(2)
    @DisplayName("提交答案：单选题答错，保存记录并加入错题本")
    void submitAnswer_singleChoiceWrong_savesRecordAndAddsToWrongBook() {
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setQuestionId(singleChoiceQId);
        request.setUserAnswer("A");

        PracticeResultVO result = practiceService.submitAnswer(request, userId);

        assertNotNull(result.getRecordId());
        assertFalse(result.getCorrect());
        assertEquals("B", result.getCorrectAnswer());

        // 验证错题本中已有该题
        LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
        wqWrapper.eq(WrongQuestion::getUserId, userId)
                 .eq(WrongQuestion::getQuestionId, singleChoiceQId);
        WrongQuestion wrongQuestion = wrongQuestionMapper.selectOne(wqWrapper);
        assertNotNull(wrongQuestion, "答错应自动加入错题本");
        assertEquals(1, wrongQuestion.getWrongCount());
        assertEquals(0, wrongQuestion.getMasteryLevel()); // 未掌握
    }

    @Test
    @Order(3)
    @DisplayName("提交答案：答对后自动从错题本移出")
    void submitAnswer_correctAfterWrong_removesFromWrongBook() {
        // 先答错一次
        PracticeSubmitRequest wrongRequest = new PracticeSubmitRequest();
        wrongRequest.setQuestionId(singleChoiceQId);
        wrongRequest.setUserAnswer("C");
        practiceService.submitAnswer(wrongRequest, userId);

        // 确认已在错题本
        LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
        wqWrapper.eq(WrongQuestion::getUserId, userId)
                 .eq(WrongQuestion::getQuestionId, singleChoiceQId);
        WrongQuestion before = wrongQuestionMapper.selectOne(wqWrapper);
        assertNotNull(before, "答错后应在错题本");

        // 再答对
        PracticeSubmitRequest correctRequest = new PracticeSubmitRequest();
        correctRequest.setQuestionId(singleChoiceQId);
        correctRequest.setUserAnswer("B");
        PracticeResultVO result = practiceService.submitAnswer(correctRequest, userId);

        assertTrue(result.getCorrect());

        // 验证错题本记录已移出（或 wrongCount 清零）
        WrongQuestion after = wrongQuestionMapper.selectById(before.getId());
        assertTrue(after == null || after.getWrongCount() == 0,
                "答对后应从错题本移出或错误次数清零");
    }

    @Test
    @Order(4)
    @DisplayName("提交答案：判断题答对")
    void submitAnswer_trueFalseCorrect_judgedCorrectly() {
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setQuestionId(trueFalseQId);
        request.setUserAnswer("FALSE");

        PracticeResultVO result = practiceService.submitAnswer(request, userId);

        assertTrue(result.getCorrect(), "判断题 FALSE 答案应判为正确");
        assertNotNull(result.getRecordId());
    }

    @Test
    @Order(5)
    @DisplayName("提交答案：题目不存在抛出业务异常")
    void submitAnswer_questionNotFound_throwsBusinessException() {
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setQuestionId(999999L);
        request.setUserAnswer("A");

        assertThrows(BusinessException.class,
                () -> practiceService.submitAnswer(request, userId));
    }

    @Test
    @Order(6)
    @DisplayName("提交答案：题目ID为null抛出业务异常")
    void submitAnswer_nullQuestionId_throwsBusinessException() {
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setQuestionId(null);
        request.setUserAnswer("A");

        assertThrows(BusinessException.class,
                () -> practiceService.submitAnswer(request, userId));
    }

    @Test
    @Order(7)
    @DisplayName("提交答案：空答案抛出业务异常")
    void submitAnswer_emptyAnswer_throwsBusinessException() {
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setQuestionId(singleChoiceQId);
        request.setUserAnswer("  ");

        assertThrows(BusinessException.class,
                () -> practiceService.submitAnswer(request, userId));
    }

    // ======================== 获取题目集成测试 ========================

    @Test
    @Order(8)
    @DisplayName("获取练习题目：返回题目列表且不暴露正确答案")
    void getPracticeQuestions_returnsQuestionsWithoutCorrectAnswer() {
        List<QuestionVO> questions = practiceService.getPracticeQuestions(
                courseId, null, null, null, 10);

        assertFalse(questions.isEmpty(), "应返回至少1道题目");
        assertEquals(4, questions.size(), "应返回4道题目");

        // 验证练习模式不暴露正确答案
        for (QuestionVO vo : questions) {
            assertNull(vo.getAnalysis(), "练习模式不应返回解析");
            if (vo.getOptions() != null) {
                vo.getOptions().forEach(opt ->
                        assertEquals(0, opt.getIsCorrect(), "练习模式选项不应标记正确答案"));
            }
        }
    }

    @Test
    @Order(9)
    @DisplayName("获取练习题目：按题型筛选")
    void getPracticeQuestions_filterByQuestionType() {
        List<QuestionVO> questions = practiceService.getPracticeQuestions(
                courseId, null, "SINGLE_CHOICE", null, 10);

        assertFalse(questions.isEmpty(), "应返回单选题");
        questions.forEach(q ->
                assertEquals("SINGLE_CHOICE", q.getQuestionType(), "筛选应只返回单选题"));
    }

    @Test
    @Order(10)
    @DisplayName("获取练习题目：按知识点筛选")
    void getPracticeQuestions_filterByKnowledgePoint() {
        List<QuestionVO> questions = practiceService.getPracticeQuestions(
                courseId, knowledgePointId, null, null, 10);

        assertFalse(questions.isEmpty(), "应返回关联知识点的题目");
        assertEquals(1, questions.size(), "只有单选题关联了该知识点");
        assertEquals(singleChoiceQId, questions.get(0).getId());
    }

    // ======================== 记录与统计集成测试 ========================

    @Test
    @Order(11)
    @DisplayName("练习记录：提交后可查询到分页记录")
    void getUserPracticeRecords_afterSubmit_returnsPagedRecords() {
        // 先做两道题
        PracticeSubmitRequest req1 = new PracticeSubmitRequest();
        req1.setQuestionId(singleChoiceQId);
        req1.setUserAnswer("B");
        practiceService.submitAnswer(req1, userId);

        PracticeSubmitRequest req2 = new PracticeSubmitRequest();
        req2.setQuestionId(trueFalseQId);
        req2.setUserAnswer("FALSE");
        practiceService.submitAnswer(req2, userId);

        // 查询记录
        Page<PracticeRecordVO> page = practiceService.getUserPracticeRecords(
                userId, 1, 10, null, null, null);

        assertTrue(page.getTotal() >= 2, "应至少有2条记录");
        // 记录应包含题目信息
        page.getRecords().forEach(record -> {
            assertNotNull(record.getQuestionId());
            assertNotNull(record.getQuestionContent());
            assertNotNull(record.getQuestionType());
        });
    }

    @Test
    @Order(12)
    @DisplayName("练习统计：提交后统计数据正确")
    void getUserPracticeStats_afterSubmit_returnsCorrectStats() {
        // 先做一道对、一道错
        PracticeSubmitRequest correctReq = new PracticeSubmitRequest();
        correctReq.setQuestionId(singleChoiceQId);
        correctReq.setUserAnswer("B");
        practiceService.submitAnswer(correctReq, userId);

        PracticeSubmitRequest wrongReq = new PracticeSubmitRequest();
        wrongReq.setQuestionId(multiChoiceQId);
        wrongReq.setUserAnswer("A"); // 少选了一个正确答案
        practiceService.submitAnswer(wrongReq, userId);

        Map<String, Object> stats = practiceService.getUserPracticeStats(userId);

        assertTrue((int) stats.get("totalAnswered") >= 2, "总刷题数应>=2");
        assertTrue((int) stats.get("correctCount") >= 1, "正确数应>=1");
        assertTrue((double) stats.get("correctRate") > 0, "正确率应>0");
    }

    // ======================== 错题重练集成测试 ========================

    @Test
    @Order(13)
    @DisplayName("错题重练：错题本中有错题时可获取重练题目")
    void getWrongQuestionPractice_withWrongQuestions_returnsQuestions() {
        // 先答错一道题
        PracticeSubmitRequest wrongReq = new PracticeSubmitRequest();
        wrongReq.setQuestionId(singleChoiceQId);
        wrongReq.setUserAnswer("A");
        practiceService.submitAnswer(wrongReq, userId);

        // 获取错题重练
        List<QuestionVO> questions = practiceService.getWrongQuestionPractice(userId, null, 10);

        assertFalse(questions.isEmpty(), "错题本有记录时应返回重练题目");
        // 验证返回的题目确实在错题本中
        List<Long> questionIds = questions.stream().map(QuestionVO::getId).toList();
        assertTrue(questionIds.contains(singleChoiceQId), "应包含答错的题目");
    }

    @Test
    @Order(14)
    @DisplayName("错题重练：错题本为空时返回空列表")
    void getWrongQuestionPractice_noWrongQuestions_returnsEmpty() {
        // 创建一个全新的用户
        User freshUser = new User();
        freshUser.setUsername("practice_fresh_user");
        freshUser.setPassword("$2a$10$dummyHashForTest");
        freshUser.setNickname("新用户");
        freshUser.setRole("USER");
        freshUser.setStatus(1);
        freshUser.setCreateTime(LocalDateTime.now());
        freshUser.setUpdateTime(LocalDateTime.now());
        freshUser.setDeleted(0);
        userMapper.insert(freshUser);

        List<QuestionVO> questions = practiceService.getWrongQuestionPractice(
                freshUser.getId(), null, 10);

        assertTrue(questions.isEmpty(), "无错题时应返回空列表");
    }

    // ======================== 收藏题练习集成测试 ========================

    @Test
    @Order(15)
    @DisplayName("收藏题练习：有收藏时可获取收藏题")
    void getFavoritePractice_withFavorites_returnsQuestions() {
        // 先收藏一道题
        UserFavoriteQuestion fav = new UserFavoriteQuestion();
        fav.setUserId(userId);
        fav.setQuestionId(singleChoiceQId);
        fav.setCreateTime(LocalDateTime.now());
        userFavoriteQuestionMapper.insert(fav);

        List<QuestionVO> questions = practiceService.getFavoritePractice(userId, 10, null);

        assertFalse(questions.isEmpty(), "有收藏时应返回题目");
        assertTrue(questions.stream().anyMatch(q -> q.getId().equals(singleChoiceQId)),
                "应包含收藏的题目");
    }

    @Test
    @Order(16)
    @DisplayName("收藏题练习：无收藏时返回空列表")
    void getFavoritePractice_noFavorites_returnsEmpty() {
        User freshUser = new User();
        freshUser.setUsername("practice_fav_fresh_user");
        freshUser.setPassword("$2a$10$dummyHashForTest");
        freshUser.setNickname("收藏新用户");
        freshUser.setRole("USER");
        freshUser.setStatus(1);
        freshUser.setCreateTime(LocalDateTime.now());
        freshUser.setUpdateTime(LocalDateTime.now());
        freshUser.setDeleted(0);
        userMapper.insert(freshUser);

        List<QuestionVO> questions = practiceService.getFavoritePractice(
                freshUser.getId(), 10, null);

        assertTrue(questions.isEmpty(), "无收藏时应返回空列表");
    }
}