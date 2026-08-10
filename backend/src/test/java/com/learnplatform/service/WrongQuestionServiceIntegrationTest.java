package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.WrongQuestionVO;
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
 * 错题本服务集成测试 —— 真实 MySQL + Flyway 迁移
 * 验证错题加入、重复累加、掌握程度管理、分页查询、统计等核心流程。
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
class WrongQuestionServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private WrongQuestionService wrongQuestionService;

    @Autowired
    private WrongQuestionMapper wrongQuestionMapper;
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private QuestionOptionMapper questionOptionMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CourseMapper courseMapper;

    private static Long userId;
    private static Long anotherUserId;
    private static Long courseId1;
    private static Long courseId2;
    private static Long questionId1; // 属于课程1
    private static Long questionId2; // 属于课程1
    private static Long questionId3; // 属于课程2

    @BeforeAll
    static void setupTestData(
            @Autowired UserMapper userMapper,
            @Autowired QuestionMapper questionMapper,
            @Autowired CourseMapper courseMapper
    ) {
        // 1. 创建用户
        User user = new User();
        user.setUsername("wq_integration_user");
        user.setPassword("$2a$10$dummyHashForTest");
        user.setNickname("错题测试用户");
        user.setRole("USER");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);
        userId = user.getId();

        // 2. 创建另一个用户（用于越权测试）
        User other = new User();
        other.setUsername("wq_integration_other");
        other.setPassword("$2a$10$dummyHashForTest");
        other.setNickname("其他用户");
        other.setRole("USER");
        other.setStatus(1);
        other.setCreateTime(LocalDateTime.now());
        other.setUpdateTime(LocalDateTime.now());
        other.setDeleted(0);
        userMapper.insert(other);
        anotherUserId = other.getId();

        // 3. 创建课程1
        Course c1 = new Course();
        c1.setName("错题测试课程A");
        c1.setDeleted(0);
        c1.setCreateTime(LocalDateTime.now());
        c1.setUpdateTime(LocalDateTime.now());
        courseMapper.insert(c1);
        courseId1 = c1.getId();

        // 4. 创建课程2
        Course c2 = new Course();
        c2.setName("错题测试课程B");
        c2.setDeleted(0);
        c2.setCreateTime(LocalDateTime.now());
        c2.setUpdateTime(LocalDateTime.now());
        courseMapper.insert(c2);
        courseId2 = c2.getId();

        // 5. 创建题目1（课程1）
        Question q1 = new Question();
        q1.setContent("错题测试题1");
        q1.setQuestionType("SINGLE_CHOICE");
        q1.setDifficulty(1);
        q1.setAnalysis("题目1解析");
        q1.setCourseId(courseId1);
        q1.setStatus(1);
        q1.setDeleted(0);
        q1.setCreateTime(LocalDateTime.now());
        q1.setUpdateTime(LocalDateTime.now());
        questionMapper.insert(q1);
        questionId1 = q1.getId();

        // 6. 创建题目2（课程1）
        Question q2 = new Question();
        q2.setContent("错题测试题2");
        q2.setQuestionType("MULTIPLE_CHOICE");
        q2.setDifficulty(2);
        q2.setAnalysis("题目2解析");
        q2.setCourseId(courseId1);
        q2.setStatus(1);
        q2.setDeleted(0);
        q2.setCreateTime(LocalDateTime.now());
        q2.setUpdateTime(LocalDateTime.now());
        questionMapper.insert(q2);
        questionId2 = q2.getId();

        // 7. 创建题目3（课程2）
        Question q3 = new Question();
        q3.setContent("错题测试题3");
        q3.setQuestionType("TRUE_FALSE");
        q3.setDifficulty(1);
        q3.setAnalysis("题目3解析");
        q3.setCourseId(courseId2);
        q3.setStatus(1);
        q3.setDeleted(0);
        q3.setCreateTime(LocalDateTime.now());
        q3.setUpdateTime(LocalDateTime.now());
        questionMapper.insert(q3);
        questionId3 = q3.getId();
    }

    // ======================== addWrongQuestion 测试 ========================

    @Test
    @Order(1)
    @DisplayName("加入错题本：新题目首次加入，wrongCount=1，masteryLevel=0")
    void addWrongQuestion_newEntry_createsWithCount1AndLevel0() {
        wrongQuestionService.addWrongQuestion(userId, questionId1, "A");

        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId1);
        WrongQuestion wq = wrongQuestionMapper.selectOne(wrapper);

        assertNotNull(wq, "错题记录应已创建");
        assertEquals(1, wq.getWrongCount(), "首次加入 wrongCount 应为 1");
        assertEquals(0, wq.getMasteryLevel(), "首次加入 masteryLevel 应为 0（未掌握）");
        assertEquals("A", wq.getLastWrongAnswer(), "应记录最后错误答案");
    }

    @Test
    @Order(2)
    @DisplayName("加入错题本：同一题再次答错，wrongCount 递增")
    void addWrongQuestion_duplicateEntry_incrementsWrongCount() {
        // 第一次答错
        wrongQuestionService.addWrongQuestion(userId, questionId2, "B");

        // 第二次答错
        wrongQuestionService.addWrongQuestion(userId, questionId2, "C");

        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId2);
        WrongQuestion wq = wrongQuestionMapper.selectOne(wrapper);

        assertNotNull(wq);
        assertEquals(2, wq.getWrongCount(), "第二次答错 wrongCount 应为 2");
        assertEquals("C", wq.getLastWrongAnswer(), "最后错误答案应更新为 C");
    }

    @Test
    @Order(3)
    @DisplayName("加入错题本：已掌握(masteryLevel=2)的题再次答错，重置为未掌握(0)")
    void addWrongQuestion_masteredQuestionResetsMastery() {
        // 先加入错题本
        wrongQuestionService.addWrongQuestion(userId, questionId3, "X");

        // 手动设为已掌握
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId3);
        WrongQuestion wq = wrongQuestionMapper.selectOne(wrapper);
        wq.setMasteryLevel(2);
        wrongQuestionMapper.updateById(wq);

        // 再次答错
        wrongQuestionService.addWrongQuestion(userId, questionId3, "Y");

        WrongQuestion updated = wrongQuestionMapper.selectById(wq.getId());
        assertEquals(2, updated.getWrongCount(), "wrongCount 应为 2");
        assertEquals(0, updated.getMasteryLevel(), "已掌握的题再次答错应重置为 0");
    }

    // ======================== getWrongQuestions 测试 ========================

    @Test
    @Order(4)
    @DisplayName("获取错题列表：分页返回包含题目和课程信息")
    void getWrongQuestions_returnsPagedResultWithDetails() {
        // 确保有数据
        wrongQuestionService.addWrongQuestion(userId, questionId1, "A");

        Page<WrongQuestionVO> page = wrongQuestionService.getWrongQuestions(
                userId, 1, 10, null, null);

        assertTrue(page.getTotal() >= 1, "应至少有1条错题记录");

        WrongQuestionVO vo = page.getRecords().stream()
                .filter(v -> v.getQuestionId().equals(questionId1))
                .findFirst()
                .orElse(null);
        assertNotNull(vo, "应包含题目1的错题记录");
        assertNotNull(vo.getQuestionContent(), "应包含题目内容");
        assertNotNull(vo.getCourseName(), "应包含课程名称");
        assertEquals("错题测试课程A", vo.getCourseName());
        assertEquals("SINGLE_CHOICE", vo.getQuestionType());
    }

    @Test
    @Order(5)
    @DisplayName("获取错题列表：按掌握程度筛选")
    void getWrongQuestions_filterByMasteryLevel() {
        // 先确保两道题都在错题本中
        wrongQuestionService.addWrongQuestion(userId, questionId1, "A");
        wrongQuestionService.addWrongQuestion(userId, questionId2, "B");

        // 将题目1设为部分掌握
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId1);
        WrongQuestion wq1 = wrongQuestionMapper.selectOne(wrapper);
        wq1.setMasteryLevel(1);
        wrongQuestionMapper.updateById(wq1);

        // 筛选 masteryLevel=1（部分掌握）
        Page<WrongQuestionVO> page = wrongQuestionService.getWrongQuestions(
                userId, 1, 10, null, 1);

        assertTrue(page.getTotal() >= 1, "应有至少1条部分掌握的错题");
        page.getRecords().forEach(vo ->
                assertEquals(1, vo.getMasteryLevel(), "筛选结果应全部为部分掌握"));

        // 筛选 masteryLevel=0（未掌握）
        Page<WrongQuestionVO> unmastered = wrongQuestionService.getWrongQuestions(
                userId, 1, 10, null, 0);
        assertTrue(unmastered.getTotal() >= 1, "应有至少1条未掌握的错题");
        unmastered.getRecords().forEach(vo ->
                assertEquals(0, vo.getMasteryLevel(), "筛选结果应全部为未掌握"));
    }

    @Test
    @Order(6)
    @DisplayName("获取错题列表：按课程筛选")
    void getWrongQuestions_filterByCourseId() {
        // 确保两门课程的题都在错题本
        wrongQuestionService.addWrongQuestion(userId, questionId1, "A");
        wrongQuestionService.addWrongQuestion(userId, questionId3, "Y");

        Page<WrongQuestionVO> page = wrongQuestionService.getWrongQuestions(
                userId, 1, 10, courseId1, null);

        assertEquals(1, page.getTotal(), "分页总数应只统计目标课程的错题");
        assertFalse(page.getRecords().isEmpty(), "应返回课程A的错题");
        page.getRecords().forEach(vo ->
                assertEquals("错题测试课程A", vo.getCourseName(),
                        "按课程筛选应只返回该课程的错题"));

        Page<WrongQuestionVO> focused = wrongQuestionService.getWrongQuestions(
                userId, 1, 10, courseId1, questionId1, null);
        assertEquals(1, focused.getTotal(), "课程深链应只返回服务端选择的目标题目");
        assertEquals(questionId1, focused.getRecords().get(0).getQuestionId());
    }

    // ======================== updateMasteryLevel 测试 ========================

    @Test
    @Order(7)
    @DisplayName("更新掌握程度：成功更新")
    void updateMasteryLevel_success() {
        wrongQuestionService.addWrongQuestion(userId, questionId1, "A");

        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId1);
        WrongQuestion wq = wrongQuestionMapper.selectOne(wrapper);

        wrongQuestionService.updateMasteryLevel(wq.getId(), userId, 1);

        WrongQuestion updated = wrongQuestionMapper.selectById(wq.getId());
        assertEquals(1, updated.getMasteryLevel(), "掌握程度应更新为 1（部分掌握）");
    }

    @Test
    @Order(8)
    @DisplayName("更新掌握程度：记录不存在抛出业务异常")
    void updateMasteryLevel_notFound_throwsBusinessException() {
        assertThrows(BusinessException.class,
                () -> wrongQuestionService.updateMasteryLevel(999999L, userId, 1));
    }

    @Test
    @Order(9)
    @DisplayName("更新掌握程度：越权操作抛出业务异常")
    void updateMasteryLevel_wrongUser_throwsBusinessException() {
        wrongQuestionService.addWrongQuestion(userId, questionId1, "A");

        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId1);
        WrongQuestion wq = wrongQuestionMapper.selectOne(wrapper);

        // 用另一个用户尝试更新
        assertThrows(BusinessException.class,
                () -> wrongQuestionService.updateMasteryLevel(wq.getId(), anotherUserId, 2));
    }

    // ======================== removeWrongQuestion 测试 ========================

    @Test
    @Order(10)
    @DisplayName("移出错题本：逻辑删除成功")
    void removeWrongQuestion_success() {
        wrongQuestionService.addWrongQuestion(userId, questionId1, "A");

        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId1);
        WrongQuestion wq = wrongQuestionMapper.selectOne(wrapper);
        Long wqId = wq.getId();

        wrongQuestionService.removeWrongQuestion(wqId, userId);

        // 逻辑删除后 selectById 返回 null（@TableLogic）
        WrongQuestion deleted = wrongQuestionMapper.selectById(wqId);
        assertNull(deleted, "移出后应查不到记录");
    }

    @Test
    @Order(11)
    @DisplayName("移出错题本：记录不存在抛出业务异常")
    void removeWrongQuestion_notFound_throwsBusinessException() {
        assertThrows(BusinessException.class,
                () -> wrongQuestionService.removeWrongQuestion(999999L, userId));
    }

    @Test
    @Order(12)
    @DisplayName("移出错题本：越权操作抛出业务异常")
    void removeWrongQuestion_wrongUser_throwsBusinessException() {
        wrongQuestionService.addWrongQuestion(userId, questionId1, "A");

        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId1);
        WrongQuestion wq = wrongQuestionMapper.selectOne(wrapper);

        assertThrows(BusinessException.class,
                () -> wrongQuestionService.removeWrongQuestion(wq.getId(), anotherUserId));
    }

    // ======================== removeOnCorrect 测试 ========================

    @Test
    @Order(13)
    @DisplayName("答对自动移出：错题本中有记录时成功移出")
    void removeOnCorrect_withRecord_removesSuccessfully() {
        wrongQuestionService.addWrongQuestion(userId, questionId2, "B");

        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId2);
        WrongQuestion before = wrongQuestionMapper.selectOne(wrapper);
        assertNotNull(before, "加入后应有错题记录");

        wrongQuestionService.removeOnCorrect(userId, questionId2);

        WrongQuestion after = wrongQuestionMapper.selectById(before.getId());
        assertNull(after, "答对后应自动移出错题本");
    }

    @Test
    @Order(14)
    @DisplayName("加入错题本：逻辑删除后再次答错应复活原记录")
    void addWrongQuestion_afterLogicalDelete_revivesExistingRecord() {
        wrongQuestionService.addWrongQuestion(userId, questionId2, "B");

        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId2);
        WrongQuestion before = wrongQuestionMapper.selectOne(wrapper);
        assertNotNull(before, "加入后应有错题记录");

        wrongQuestionService.removeOnCorrect(userId, questionId2);
        assertNull(wrongQuestionMapper.selectById(before.getId()), "移出后普通查询应查不到逻辑删除记录");

        wrongQuestionService.addWrongQuestion(userId, questionId2, "C");

        WrongQuestion revived = wrongQuestionMapper.selectOne(wrapper);
        assertNotNull(revived, "再次答错应复活错题记录");
        assertEquals(before.getId(), revived.getId(), "应复活原记录而不是插入新记录");
        assertEquals(2, revived.getWrongCount(), "复活后 wrongCount 应继续递增");
        assertEquals(0, revived.getMasteryLevel(), "复活后应回到未掌握");
        assertEquals("C", revived.getLastWrongAnswer(), "应记录最新错误答案");
        assertEquals(0, revived.getDeleted(), "复活后 deleted 应为 0");
    }

    @Test
    @Order(15)
    @DisplayName("答对自动移出：错题本中无记录时无操作")
    void removeOnCorrect_noRecord_noException() {
        // 使用一个不在错题本中的题目
        assertDoesNotThrow(() ->
                wrongQuestionService.removeOnCorrect(userId, 999999L));
    }

    // ======================== getWrongQuestionStats 测试 ========================

    @Test
    @Order(16)
    @DisplayName("错题统计：返回正确的统计信息和课程分布")
    void getWrongQuestionStats_returnsCorrectStats() {
        // 确保有不同掌握程度的错题
        wrongQuestionService.addWrongQuestion(userId, questionId1, "A");
        wrongQuestionService.addWrongQuestion(userId, questionId3, "X");

        // 将题目3设为已掌握
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId3);
        WrongQuestion wq3 = wrongQuestionMapper.selectOne(wrapper);
        wq3.setMasteryLevel(2);
        wrongQuestionMapper.updateById(wq3);

        Map<String, Object> stats = wrongQuestionService.getWrongQuestionStats(userId);

        assertTrue((int) stats.get("total") >= 2, "总错题数应>=2");
        assertTrue((int) stats.get("unmastered") >= 1, "未掌握数应>=1");
        assertTrue((int) stats.get("mastered") >= 1, "已掌握数应>=1");

        // 验证课程分布
        @SuppressWarnings("unchecked")
        Map<String, Integer> courseWrongCount = (Map<String, Integer>) stats.get("courseWrongCount");
        assertNotNull(courseWrongCount, "课程分布不应为 null");
        assertTrue(courseWrongCount.containsKey("错题测试课程A"),
                "课程分布应包含错题测试课程A");
    }

    @Test
    @Order(17)
    @DisplayName("错题统计：空错题本返回零值统计")
    void getWrongQuestionStats_emptyBook_returnsZeros() {
        User freshUser = new User();
        freshUser.setUsername("wq_stats_fresh_user");
        freshUser.setPassword("$2a$10$dummyHashForTest");
        freshUser.setNickname("统计新用户");
        freshUser.setRole("USER");
        freshUser.setStatus(1);
        freshUser.setCreateTime(LocalDateTime.now());
        freshUser.setUpdateTime(LocalDateTime.now());
        freshUser.setDeleted(0);
        userMapper.insert(freshUser);

        Map<String, Object> stats = wrongQuestionService.getWrongQuestionStats(freshUser.getId());

        assertEquals(0, stats.get("total"), "无错题时 total 应为 0");
        assertEquals(0, stats.get("unmastered"));
        assertEquals(0, stats.get("partial"));
        assertEquals(0, stats.get("mastered"));

        @SuppressWarnings("unchecked")
        Map<String, Integer> courseWrongCount = (Map<String, Integer>) stats.get("courseWrongCount");
        assertNotNull(courseWrongCount);
        assertTrue(courseWrongCount.isEmpty(), "无错题时课程分布应为空");
    }
}
