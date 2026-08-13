package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.ExamTimedOutException;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import com.learnplatform.IntegrationTestBase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 考试服务集成测试 —— 真实 MySQL + Flyway 迁移
 * 验证考试提交流程中的事务、行锁、判分、错题归集和业务约束。
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
class ExamServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ExamService examService;

    @Autowired
    private ExamRecordMapper examRecordMapper;
    @Autowired
    private ExamAnswerMapper examAnswerMapper;
    @Autowired
    private ExamPaperMapper examPaperMapper;
    @Autowired
    private ExamQuestionMapper examQuestionMapper;
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private QuestionOptionMapper questionOptionMapper;
    @Autowired
    private WrongQuestionMapper wrongQuestionMapper;
    @Autowired
    private UserMapper userMapper;

    private static Long userId;
    private static Long question1Id; // 单选题
    private static Long question2Id; // 多选题
    private static Long examPaperId;
    private static Long examRecordId;

    @BeforeAll
    static void setupTestData(
            @Autowired UserMapper userMapper,
            @Autowired CourseMapper courseMapper,
            @Autowired QuestionMapper questionMapper,
            @Autowired QuestionOptionMapper questionOptionMapper,
            @Autowired ExamPaperMapper examPaperMapper,
            @Autowired ExamQuestionMapper examQuestionMapper
    ) {
        // 1. 创建用户
        User user = new User();
        user.setUsername("exam_integration_user");
        user.setPassword("$2a$10$dummyHashForTest");
        user.setNickname("考试测试用户");
        user.setRole("USER");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);
        userId = user.getId();

        Course course = new Course();
        course.setName("考试集成测试课程");
        course.setDeleted(0);
        course.setCreateTime(LocalDateTime.now());
        course.setUpdateTime(LocalDateTime.now());
        courseMapper.insert(course);
        Long courseId = course.getId();

        // 2. 创建单选题 (SINGLE_CHOICE)
        Question q1 = new Question();
        q1.setContent("1+1=?");
        q1.setQuestionType("SINGLE_CHOICE");
        q1.setCourseId(courseId);
        q1.setDifficulty(1);
        q1.setAnalysis("1+1=2");
        q1.setDeleted(0);
        q1.setCreateTime(LocalDateTime.now());
        q1.setUpdateTime(LocalDateTime.now());
        questionMapper.insert(q1);
        question1Id = q1.getId();

        QuestionOption opt1a = new QuestionOption();
        opt1a.setQuestionId(question1Id);
        opt1a.setOptionLabel("A");
        opt1a.setContent("2");
        opt1a.setIsCorrect(1);
        opt1a.setSortOrder(1);
        questionOptionMapper.insert(opt1a);

        QuestionOption opt1b = new QuestionOption();
        opt1b.setQuestionId(question1Id);
        opt1b.setOptionLabel("B");
        opt1b.setContent("3");
        opt1b.setIsCorrect(0);
        opt1b.setSortOrder(2);
        questionOptionMapper.insert(opt1b);

        // 3. 创建多选题 (MULTIPLE_CHOICE)
        Question q2 = new Question();
        q2.setContent("以下哪些是偶数?");
        q2.setQuestionType("MULTIPLE_CHOICE");
        q2.setCourseId(courseId);
        q2.setDifficulty(2);
        q2.setAnalysis("2和4是偶数");
        q2.setDeleted(0);
        q2.setCreateTime(LocalDateTime.now());
        q2.setUpdateTime(LocalDateTime.now());
        questionMapper.insert(q2);
        question2Id = q2.getId();

        QuestionOption opt2a = new QuestionOption();
        opt2a.setQuestionId(question2Id);
        opt2a.setOptionLabel("A");
        opt2a.setContent("2");
        opt2a.setIsCorrect(1);
        opt2a.setSortOrder(1);
        questionOptionMapper.insert(opt2a);

        QuestionOption opt2b = new QuestionOption();
        opt2b.setQuestionId(question2Id);
        opt2b.setOptionLabel("B");
        opt2b.setContent("3");
        opt2b.setIsCorrect(0);
        opt2b.setSortOrder(2);
        questionOptionMapper.insert(opt2b);

        QuestionOption opt2c = new QuestionOption();
        opt2c.setQuestionId(question2Id);
        opt2c.setOptionLabel("C");
        opt2c.setContent("4");
        opt2c.setIsCorrect(1);
        opt2c.setSortOrder(3);
        questionOptionMapper.insert(opt2c);

        // 4. 创建考试试卷
        ExamPaper paper = new ExamPaper();
        paper.setTitle("集成测试试卷");
        paper.setDuration(60);
        paper.setTotalScore(20);
        paper.setStatus(1); // 已发布
        paper.setDeleted(0);
        paper.setCreateTime(LocalDateTime.now());
        paper.setUpdateTime(LocalDateTime.now());
        examPaperMapper.insert(paper);
        examPaperId = paper.getId();

        // 5. 关联题目到试卷
        ExamQuestion eq1 = new ExamQuestion();
        eq1.setExamPaperId(examPaperId);
        eq1.setQuestionId(question1Id);
        eq1.setScore(10);
        eq1.setSortOrder(1);
        examQuestionMapper.insert(eq1);

        ExamQuestion eq2 = new ExamQuestion();
        eq2.setExamPaperId(examPaperId);
        eq2.setQuestionId(question2Id);
        eq2.setScore(10);
        eq2.setSortOrder(2);
        examQuestionMapper.insert(eq2);
    }

    // ======================== 考试流程集成测试 ========================

    @Test
    @Order(1)
    @DisplayName("开始考试：创建考试记录并关联已发布试卷")
    void startExam_createsRecordWithCorrectPaper() {
        ExamRecordVO vo = examService.startExam(examPaperId, userId);

        assertNotNull(vo.getId());
        assertEquals(examPaperId, vo.getExamPaperId());
        assertEquals(0, vo.getStatus()); // 进行中
        assertNotNull(vo.getStartTime());
        assertNull(vo.getEndTime());
        assertEquals("集成测试试卷", vo.getExamTitle());
        assertEquals(60, vo.getDuration());

        examRecordId = vo.getId();
    }

    @Test
    @Order(2)
    @DisplayName("开始考试：未发布试卷应抛出业务异常")
    void startExam_unpublishedPaper_throwsBusinessException() {
        // 创建一个草稿试卷
        ExamPaper draftPaper = new ExamPaper();
        draftPaper.setTitle("草稿试卷");
        draftPaper.setDuration(30);
        draftPaper.setTotalScore(10);
        draftPaper.setStatus(0); // 草稿
        draftPaper.setDeleted(0);
        draftPaper.setCreateTime(LocalDateTime.now());
        draftPaper.setUpdateTime(LocalDateTime.now());
        examPaperMapper.insert(draftPaper);

        assertThrows(BusinessException.class,
                () -> examService.startExam(draftPaper.getId(), userId));
    }

    @Test
    @Order(3)
    @DisplayName("提交考试：全部答对得满分")
    void submitExam_allCorrect_earnsFullScore() {
        // 先开始考试
        ExamRecordVO record = examService.startExam(examPaperId, userId);

        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setExamRecordId(record.getId());

        ExamSubmitRequest.AnswerItem ans1 = new ExamSubmitRequest.AnswerItem();
        ans1.setQuestionId(question1Id);
        ans1.setUserAnswer("A");

        ExamSubmitRequest.AnswerItem ans2 = new ExamSubmitRequest.AnswerItem();
        ans2.setQuestionId(question2Id);
        ans2.setUserAnswer("A,C");

        request.setAnswers(List.of(ans1, ans2));

        ExamRecordVO result = examService.submitExam(request, userId);

        assertEquals(1, result.getStatus()); // 已完成
        assertEquals(20, result.getScore()); // 满分
        assertEquals(20, result.getTotalScore());
        assertNotNull(result.getEndTime());
        assertNotNull(result.getAnswers());
        assertEquals(2, result.getAnswers().size());

        // 验证答题详情
        long correctCount = result.getAnswers().stream()
                .filter(a -> a.getIsCorrect() != null && a.getIsCorrect() == 1)
                .count();
        assertEquals(2, correctCount, "两道题应全部判为正确");

        // 验证没有错题记录
        LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
        wqWrapper.eq(WrongQuestion::getUserId, userId);
        long wrongCount = wrongQuestionMapper.selectCount(wqWrapper);
        assertEquals(0, wrongCount, "全部答对不应产生错题记录");
    }

    @Test
    @Order(4)
    @DisplayName("提交考试：答错题目应自动加入错题本")
    void submitExam_wrongAnswers_addsToWrongQuestionBook() {
        ExamRecordVO record = examService.startExam(examPaperId, userId);

        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setExamRecordId(record.getId());

        // 单选题答错
        ExamSubmitRequest.AnswerItem ans1 = new ExamSubmitRequest.AnswerItem();
        ans1.setQuestionId(question1Id);
        ans1.setUserAnswer("B");

        // 多选题答错（只选了一个正确答案）
        ExamSubmitRequest.AnswerItem ans2 = new ExamSubmitRequest.AnswerItem();
        ans2.setQuestionId(question2Id);
        ans2.setUserAnswer("A");

        request.setAnswers(List.of(ans1, ans2));

        ExamRecordVO result = examService.submitExam(request, userId);

        assertEquals(1, result.getStatus());
        assertEquals(0, result.getScore()); // 全部答错
        assertEquals(20, result.getTotalScore());

        // 验证错题本有两条记录
        LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
        wqWrapper.eq(WrongQuestion::getUserId, userId);
        List<WrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(wqWrapper);
        assertEquals(2, wrongQuestions.size(), "答错两题应产生两条错题记录");

        // 验证错题关联了正确的题目
        List<Long> wrongQuestionIds = wrongQuestions.stream()
                .map(WrongQuestion::getQuestionId)
                .toList();
        assertTrue(wrongQuestionIds.contains(question1Id));
        assertTrue(wrongQuestionIds.contains(question2Id));
    }

    @Test
    @Order(5)
    @DisplayName("提交考试：已提交的考试不能再次提交")
    void submitExam_alreadySubmitted_throwsBusinessException() {
        ExamRecordVO record = examService.startExam(examPaperId, userId);

        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setExamRecordId(record.getId());

        ExamSubmitRequest.AnswerItem ans = new ExamSubmitRequest.AnswerItem();
        ans.setQuestionId(question1Id);
        ans.setUserAnswer("A");
        request.setAnswers(List.of(ans));

        // 第一次提交
        examService.submitExam(request, userId);

        // 第二次提交应抛出异常
        BusinessException ex = assertThrows(BusinessException.class,
                () -> examService.submitExam(request, userId));
        assertTrue(ex.getMessage().contains("已结束"));
    }

    @Test
    @Order(6)
    @DisplayName("提交考试：非试卷题目应抛出校验异常")
    void submitExam_questionNotInPaper_throwsValidationException() {
        ExamRecordVO record = examService.startExam(examPaperId, userId);

        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setExamRecordId(record.getId());

        ExamSubmitRequest.AnswerItem ans = new ExamSubmitRequest.AnswerItem();
        ans.setQuestionId(999999L); // 不存在的题目
        ans.setUserAnswer("A");
        request.setAnswers(List.of(ans));

        assertThrows(BusinessException.class,
                () -> examService.submitExam(request, userId));
    }

    @Test
    @Order(7)
    @DisplayName("提交考试：越权提交他人考试记录应被拒绝")
    void submitExam_otherUsersRecord_throwsForbiddenException() {
        ExamRecordVO record = examService.startExam(examPaperId, userId);

        // 创建另一个用户
        User otherUser = new User();
        otherUser.setUsername("exam_other_user");
        otherUser.setPassword("$2a$10$dummyHashForTest");
        otherUser.setNickname("其他用户");
        otherUser.setRole("USER");
        otherUser.setStatus(1);
        otherUser.setCreateTime(LocalDateTime.now());
        otherUser.setUpdateTime(LocalDateTime.now());
        otherUser.setDeleted(0);
        userMapper.insert(otherUser);

        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setExamRecordId(record.getId());

        ExamSubmitRequest.AnswerItem ans = new ExamSubmitRequest.AnswerItem();
        ans.setQuestionId(question1Id);
        ans.setUserAnswer("2");
        request.setAnswers(List.of(ans));

        assertThrows(BusinessException.class,
                () -> examService.submitExam(request, otherUser.getId()));
    }

    @Test
    @Order(8)
    @DisplayName("提交考试：超时提交应标记考试超时")
    void submitExam_timedOut_marksRecordAsTimedOut() {
        // 创建一个超时的考试记录（手动设置开始时间为2小时前，限时1分钟）
        ExamRecord record = new ExamRecord();
        record.setUserId(userId);
        record.setExamPaperId(examPaperId);
        record.setStartTime(LocalDateTime.now().minusHours(2));
        record.setTotalScore(20);
        record.setStatus(0); // 进行中
        record.setActiveExamKey("EXAM:" + userId + ":" + examPaperId);
        examRecordMapper.insert(record);

        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setExamRecordId(record.getId());

        ExamSubmitRequest.AnswerItem ans = new ExamSubmitRequest.AnswerItem();
        ans.setQuestionId(question1Id);
        ans.setUserAnswer("2");
        request.setAnswers(List.of(ans));

        // 超时提交应抛出 ExamTimedOutException
        assertThrows(ExamTimedOutException.class,
                () -> examService.submitExam(request, userId));

        // 验证考试记录已标记为超时（status=2）
        ExamRecord updated = examRecordMapper.selectById(record.getId());
        assertEquals(2, updated.getStatus(), "超时考试状态应为2");
        assertEquals(0, updated.getScore(), "超时考试分数应为0");
        assertNull(updated.getActiveExamKey(), "超时后应释放活动考试键");
    }

    @Test
    @Order(9)
    @DisplayName("exam_answer 唯一约束：同一考试同一题不能重复保存")
    void examAnswer_uniqueConstraint_preventsDuplicates() {
        ExamRecordVO record = examService.startExam(examPaperId, userId);

        // 手动插入一条答题记录
        ExamAnswer existingAnswer = new ExamAnswer();
        existingAnswer.setExamRecordId(record.getId());
        existingAnswer.setQuestionId(question1Id);
        existingAnswer.setUserAnswer("2");
        existingAnswer.setIsCorrect(1);
        existingAnswer.setScore(10);
        examAnswerMapper.insert(existingAnswer);

        // 再次插入相同 examRecordId + questionId 应违反唯一约束
        ExamAnswer duplicate = new ExamAnswer();
        duplicate.setExamRecordId(record.getId());
        duplicate.setQuestionId(question1Id);
        duplicate.setUserAnswer("3");
        duplicate.setIsCorrect(0);
        duplicate.setScore(0);

        assertThrows(Exception.class,
                () -> examAnswerMapper.insert(duplicate),
                "唯一约束应阻止重复答题记录");
    }

    @Test
    @Order(10)
    @DisplayName("Flyway 迁移验证：exam_answer 表应存在唯一约束")
    void flyway_examAnswerTable_hasUniqueConstraint() {
        // 通过直接插入两条相同记录来间接验证唯一约束存在
        // 如果约束不存在，两条都会成功；如果存在，第二条会失败
        ExamRecord record = new ExamRecord();
        record.setUserId(userId);
        record.setExamPaperId(examPaperId);
        record.setStartTime(LocalDateTime.now());
        record.setTotalScore(20);
        record.setStatus(0);
        examRecordMapper.insert(record);

        ExamAnswer answer1 = new ExamAnswer();
        answer1.setExamRecordId(record.getId());
        answer1.setQuestionId(question1Id);
        answer1.setUserAnswer("A");
        answer1.setIsCorrect(1);
        answer1.setScore(10);
        examAnswerMapper.insert(answer1);

        ExamAnswer answer2 = new ExamAnswer();
        answer2.setExamRecordId(record.getId());
        answer2.setQuestionId(question1Id);
        answer2.setUserAnswer("B");
        answer2.setIsCorrect(0);
        answer2.setScore(0);

        // 应该违反唯一约束
        assertThrows(Exception.class,
                () -> examAnswerMapper.insert(answer2),
                "exam_answer 唯一约束 (exam_record_id, question_id) 应生效");
    }

    @Test
    @Order(11)
    @DisplayName("活动考试：重复开始恢复同一记录，提交后允许新一轮")
    void activeExam_reusesRecordUntilSubmissionReleasesIt() {
        ExamRecordVO first = examService.startExam(examPaperId, userId);
        ExamRecordVO resumed = examService.startExam(examPaperId, userId);

        assertEquals(first.getId(), resumed.getId());
        assertNotNull(examRecordMapper.selectById(first.getId()).getActiveExamKey());

        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setExamRecordId(first.getId());
        ExamSubmitRequest.AnswerItem answer1 = new ExamSubmitRequest.AnswerItem();
        answer1.setQuestionId(question1Id);
        answer1.setUserAnswer("A");
        ExamSubmitRequest.AnswerItem answer2 = new ExamSubmitRequest.AnswerItem();
        answer2.setQuestionId(question2Id);
        answer2.setUserAnswer("A,C");
        request.setAnswers(List.of(answer1, answer2));
        examService.submitExam(request, userId);

        assertNull(examRecordMapper.selectById(first.getId()).getActiveExamKey());
        ExamRecordVO nextAttempt = examService.startExam(examPaperId, userId);
        assertNotEquals(first.getId(), nextAttempt.getId());
    }
}
