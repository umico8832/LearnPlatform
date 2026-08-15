package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.dto.CourseStageAssessmentCreateRequest;
import com.learnplatform.dto.CourseStageAssessmentSubmitRequest;
import com.learnplatform.dto.CourseStageAssessmentVO;
import com.learnplatform.dto.CourseStageAssessmentSummaryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class CourseStageAssessmentIntegrationTest extends IntegrationTestBase {
    private static final String USERNAME = "stage-assessment-test";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CourseStageAssessmentService service;
    @Autowired private CourseOverviewService overviewService;

    @AfterEach
    void cleanUp() {
        Long userId = jdbcTemplate.query("SELECT id FROM user WHERE username = ?",
                result -> result.next() ? result.getLong(1) : null, USERNAME);
        if (userId == null) return;
        jdbcTemplate.update("DELETE FROM course_learning_event WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM question_review_schedule WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM wrong_question WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE q FROM course_stage_assessment_question q "
                + "JOIN course_stage_assessment a ON a.id = q.assessment_id WHERE a.user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM course_stage_assessment WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_course WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM question_knowledge_point WHERE knowledge_point_id IN "
                + "(SELECT id FROM knowledge_point WHERE name LIKE '测试限定知识点%' AND course_id IN (1,2))");
        jdbcTemplate.update("DELETE FROM question_option WHERE question_id IN "
                + "(SELECT id FROM question WHERE content LIKE '测试限定知识点%')");
        jdbcTemplate.update("DELETE FROM question WHERE content LIKE '测试限定知识点%'");
        jdbcTemplate.update("DELETE FROM knowledge_point WHERE name LIKE '测试限定知识点%'");
        jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
    }

    @Test
    void createsSnapshotGradesOnceAndWritesCourseFacts() {
        jdbcTemplate.update("INSERT INTO user (username,password,role,status,deleted) VALUES (?,'test','USER',1,0)",
                USERNAME);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, USERNAME);
        jdbcTemplate.update("INSERT INTO user_course (user_id,course_id) VALUES (?,1)", userId);
        CourseStageAssessmentCreateRequest create = new CourseStageAssessmentCreateRequest();
        create.setQuestionCount(3);

        CourseStageAssessmentVO started = service.start(userId, 1L, create);

        assertEquals("COURSE_SEQUENCE_FALLBACK", started.getSelectionStrategy());
        assertEquals(3, started.getQuestions().size());
        started.getQuestions().forEach(question -> {
            assertNull(question.getCorrectAnswer());
            assertNull(question.getAnalysis());
        });
        CourseStageAssessmentVO resumed = service.start(userId, 1L, create);
        assertEquals(started.getId(), resumed.getId());

        CourseStageAssessmentSubmitRequest submit = new CourseStageAssessmentSubmitRequest();
        submit.setAnswers(List.of(
                answer(started.getQuestions().get(0).getId(), "A"),
                answer(started.getQuestions().get(1).getId(), "B"),
                answer(started.getQuestions().get(2).getId(), "TRUE")));
        CourseStageAssessmentVO completed = service.submit(started.getId(), userId, submit);

        assertEquals("COMPLETED", completed.getStatus());
        assertEquals(2, completed.getCorrectCount());
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_learning_event WHERE user_id = ? "
                        + "AND event_source = 'STAGE_ASSESSMENT'", Integer.class, userId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wrong_question WHERE user_id = ? AND deleted = 0", Integer.class, userId));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_review_schedule WHERE user_id = ?", Integer.class, userId));
        Page<CourseStageAssessmentSummaryVO> history = service.listCompleted(userId, 1L, 1, 10);
        assertEquals(1, history.getTotal());
        assertEquals(completed.getId(), history.getRecords().get(0).getId());
        assertEquals("A", service.getCompleted(completed.getId(), userId)
                .getQuestions().get(0).getCorrectAnswer());
        CourseOverviewVO overview = overviewService.getOverview(userId, 1L);
        assertEquals(completed.getId(), overview.getLatestStageAssessment().getId());
        CourseStageAssessmentVO restarted = service.start(userId, 1L, create);
        assertNotEquals(completed.getId(), restarted.getId());
        assertEquals("IN_PROGRESS", restarted.getStatus());
    }

    @Test
    void restrictsScopedAssessmentToReviewedKnowledgePointQuestions() {
        jdbcTemplate.update("INSERT INTO user (username,password,role,status,deleted) VALUES (?,'test','USER',1,0)",
                USERNAME);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, USERNAME);
        jdbcTemplate.update("INSERT INTO user_course (user_id,course_id) VALUES (?,1)", userId);

        Long kpId = insertKnowledgePoint(1L, "REVIEWED");
        Long kpOtherCourse = insertKnowledgePoint(2L, "REVIEWED");
        Long kpUnreviewed = insertKnowledgePoint(1L, null);
        Long kpEmpty = insertKnowledgePoint(1L, "REVIEWED");
        Long qA = insertQuestion(1L, "限定范围单选题", "SINGLE_CHOICE", "A");
        Long qB = insertQuestion(1L, "限定范围判断题", "TRUE_FALSE", "A");
        Long qOutside = insertQuestion(1L, "范围外题目", "SINGLE_CHOICE", "B");
        jdbcTemplate.update("INSERT INTO question_knowledge_point (question_id,knowledge_point_id) VALUES (?,?)",
                qA, kpId);
        jdbcTemplate.update("INSERT INTO question_knowledge_point (question_id,knowledge_point_id) VALUES (?,?)",
                qB, kpId);

        CourseStageAssessmentCreateRequest scoped = new CourseStageAssessmentCreateRequest();
        scoped.setQuestionCount(5);
        scoped.setKnowledgePointId(kpId);
        CourseStageAssessmentVO started = service.start(userId, 1L, scoped);

        assertEquals(kpId, started.getTargetKnowledgePointId());
        assertEquals("测试限定知识点", started.getTargetKnowledgePointName());
        assertEquals(2, started.getQuestions().size());
        List<Long> selectedIds = started.getQuestions().stream()
                .map(question -> question.getQuestionId()).toList();
        assertTrue(selectedIds.contains(qA) && selectedIds.contains(qB));
        assertTrue(!selectedIds.contains(qOutside));
        started.getQuestions().forEach(question -> {
            assertEquals(1, question.getKnowledgePoints().size());
            assertEquals(kpId, question.getKnowledgePoints().get(0).getId());
            assertEquals("测试限定知识点", question.getKnowledgePoints().get(0).getName());
        });

        CourseStageAssessmentSubmitRequest submit = new CourseStageAssessmentSubmitRequest();
        submit.setAnswers(List.of(
                answer(started.getQuestions().get(0).getId(), "A"),
                answer(started.getQuestions().get(1).getId(), "TRUE")));
        CourseStageAssessmentVO completed = service.submit(started.getId(), userId, submit);
        assertEquals(2, completed.getCorrectCount());
        CourseStageAssessmentVO detail = service.getCompleted(started.getId(), userId);
        assertEquals(kpId, detail.getTargetKnowledgePointId());
        assertEquals("测试限定知识点", detail.getTargetKnowledgePointName());
        assertEquals(1, detail.getQuestions().get(0).getKnowledgePoints().size());
        assertEquals("测试限定知识点", detail.getQuestions().get(0).getKnowledgePoints().get(0).getName());
        assertEquals(1, detail.getKnowledgePointSummary().size());
        assertEquals(kpId, detail.getKnowledgePointSummary().get(0).getId());
        assertEquals("测试限定知识点", detail.getKnowledgePointSummary().get(0).getName());
        assertEquals(2, detail.getKnowledgePointSummary().get(0).getQuestionCount());
        assertEquals(2, detail.getKnowledgePointSummary().get(0).getCorrectCount());
        Page<CourseStageAssessmentSummaryVO> history = service.listCompleted(userId, 1L, 1, 10);
        assertEquals("测试限定知识点", history.getRecords().get(0).getTargetKnowledgePointName());

        CourseStageAssessmentCreateRequest crossCourse = new CourseStageAssessmentCreateRequest();
        crossCourse.setKnowledgePointId(kpOtherCourse);
        BusinessException crossCourseError = assertThrows(BusinessException.class,
                () -> service.start(userId, 1L, crossCourse));
        assertEquals("知识点不属于当前课程或尚未通过内容审查", crossCourseError.getMessage());

        CourseStageAssessmentCreateRequest unreviewed = new CourseStageAssessmentCreateRequest();
        unreviewed.setKnowledgePointId(kpUnreviewed);
        BusinessException unreviewedError = assertThrows(BusinessException.class,
                () -> service.start(userId, 1L, unreviewed));
        assertEquals("知识点不属于当前课程或尚未通过内容审查", unreviewedError.getMessage());

        CourseStageAssessmentCreateRequest noCandidate = new CourseStageAssessmentCreateRequest();
        noCandidate.setKnowledgePointId(kpEmpty);
        BusinessException noCandidateError = assertThrows(BusinessException.class,
                () -> service.start(userId, 1L, noCandidate));
        assertEquals("该知识点暂无可用于阶段测评的客观题", noCandidateError.getMessage());
    }

    private Long insertKnowledgePoint(Long courseId, String reviewStatus) {
        jdbcTemplate.update("INSERT INTO knowledge_point "
                        + "(name, description, course_id, parent_id, content_review_status, sort_order, deleted) "
                        + "VALUES ('测试限定知识点', '集成测试用', ?, 0, ?, 99, 0)",
                courseId, reviewStatus);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long insertQuestion(Long courseId, String content, String questionType, String correctLabel) {
        boolean isTrueFalse = "TRUE_FALSE".equals(questionType);
        jdbcTemplate.update("INSERT INTO question "
                        + "(content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted) "
                        + "VALUES (?, ?, ?, 2, '测试解析', '集成测试', 2, 1, 1, 0)",
                "测试限定知识点" + content, questionType, courseId);
        Long questionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("INSERT INTO question_option "
                        + "(question_id, content, option_label, is_correct, sort_order) VALUES (?, ?, 'A', ?, 1)",
                questionId, isTrueFalse ? "正确" : "正确选项", "A".equals(correctLabel) ? 1 : 0);
        jdbcTemplate.update("INSERT INTO question_option "
                        + "(question_id, content, option_label, is_correct, sort_order) VALUES (?, ?, 'B', ?, 2)",
                questionId, isTrueFalse ? "错误" : "干扰选项", "B".equals(correctLabel) ? 1 : 0);
        return questionId;
    }

    private CourseStageAssessmentSubmitRequest.Answer answer(Long id, String value) {
        CourseStageAssessmentSubmitRequest.Answer answer = new CourseStageAssessmentSubmitRequest.Answer();
        answer.setAssessmentQuestionId(id);
        answer.setUserAnswer(value);
        return answer;
    }
}
