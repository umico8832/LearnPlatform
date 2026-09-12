package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.CourseKnowledgePointFactVO;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.entity.Question;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class CourseLearningFactIntegrationTest extends IntegrationTestBase {
    private static final long USER = 960001;
    private static final long OTHER = 960002;
    private static final long COURSE = 960010;
    private static final long K1 = 960011;
    private static final long K2 = 960012;
    private static final long K3 = 960013;
    private static final long Q1 = 960021;
    private static final long Q2 = 960022;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SqlSessionTemplate sqlSessionTemplate;
    @Autowired private CourseOverviewService overviewService;
    @Autowired private CourseLearningEventService eventService;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO course (id,name) VALUES (?, '知识点事实测试'), (?, '空课程')", COURSE, COURSE + 90);
        jdbc.update("INSERT INTO user_course (user_id,course_id,create_time) VALUES (?,?,'2000-01-01'),"
                + "(?,?,'2000-01-01'),(?,?,'2000-01-01')", USER, COURSE, OTHER, COURSE, USER, COURSE + 90);
        for (long id : List.of(K1, K2, K3)) {
            jdbc.update("INSERT INTO knowledge_point (id,name,course_id,sort_order) VALUES (?,?,?,?)",
                    id, "知识点 " + id, COURSE, id - K1);
        }
        question(Q1, "PUBLIC", null);
        question(Q2, "PUBLIC", null);
        jdbc.update("INSERT INTO question_knowledge_point (question_id,knowledge_point_id) VALUES (?,?),(?,?)",
                Q1, K1, Q1, K2);
    }

    @Test
    void mixedSourcesAndEventReplaysCountEachAnswerOnceWithoutCountingAiOrBrowsing() {
        practice(961001, USER, Q1, 1, "A");
        practice(961002, USER, Q1, 0, "B");
        Question question = eventQuestion(Q1);
        eventService.recordQuestionAnswer(USER, question, "PRACTICE_ANSWERED", "PRACTICE", 961001L, true, null);
        eventService.recordQuestionAnswer(USER, question, "PRACTICE_ANSWERED", "PRACTICE", 961001L, true, null);
        eventService.recordQuestionAnswer(USER, question, "REVIEW_ANSWERED", "REVIEW", 961002L, false, null);
        eventService.recordQuestionAnswer(USER, question, "PRACTICE_ANSWERED", "PRACTICE", 961002L, false, null);
        paperAnswer(962001, USER, Q1, 1);
        tutorAnswer(963001, 1);
        jdbc.update("""
                INSERT INTO ai_variant_training (id,user_id,question_id,asset_id,status,user_answer,is_correct,answered_time)
                VALUES (963001,?,?,963001,'COMPLETED','B',0,CURRENT_TIMESTAMP)
                """, USER, Q1);
        eventService.recordTutorCheck(USER, COURSE, K1, 963001L, true);
        eventService.recordQuestionAnswer(USER, question, "AI_VARIANT_ANSWERED", "AI_TUTOR", 963001L, false, null);
        examAnswer(964001, Q1, 1, "A", "AUTO_GRADED");
        stageAnswer(965001, Q1, "[{\"id\":" + K1 + ",\"name\":\"快照一\"},{\"id\":"
                + K2 + ",\"name\":\"快照二\"}]", 1);
        eventService.recordPaperLearningAiAssistance(USER, COURSE, Q1, 966001L, 962001L, 1L,
                "EXPLAIN", null, LocalDateTime.now().plusMinutes(1));
        eventService.recordQuestionAnswer(USER, question, "QUESTION_VIEWED", "BROWSER", 966002L, true, null);

        CourseOverviewVO overview = overviewService.getOverview(USER, COURSE);

        assertEquals(7, overview.getAnsweredCount());
        assertEquals(5, overview.getCorrectCount());
        assertNotNull(overview.getLastLearningTime());
        assertEquals(7, point(K1).getAnsweredCount());
        assertEquals(6, point(K2).getAnsweredCount());
        assertEquals(5, point(K1).getCorrectCount());
        assertEquals(4, point(K2).getCorrectCount());
        assertEquals(0, point(K3).getAnsweredCount());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM course_learning_event WHERE user_id=? "
                + "AND event_source='AI_TUTOR' AND source_record_id=963001", Integer.class, USER));
    }

    @Test
    void aiAssistanceAloneIsActivityButNeverAnAnswer() {
        eventService.recordPaperLearningAiAssistance(USER, COURSE, Q1, 966001L, 962001L, 1L,
                "EXPLAIN", null, LocalDateTime.now());

        CourseOverviewVO overview = overviewService.getOverview(USER, COURSE);

        assertEquals(0, overview.getAnsweredCount());
        assertEquals(0, overview.getCorrectCount());
        assertNotNull(overview.getLastLearningTime());
        assertEquals(0, point(K1).getAnsweredCount());
    }

    @Test
    void excludesBlankUngradedAndPreEnrollmentAnswersAndReadsCurrentManualGradeOnce() {
        practice(961001, USER, Q1, 1, "A");
        jdbc.update("UPDATE practice_record SET create_time='1999-01-01' WHERE id=961001");
        eventService.recordQuestionAnswer(USER, eventQuestion(Q1), "PRACTICE_ANSWERED", "PRACTICE",
                961001L, true, LocalDateTime.of(1999, 1, 1, 0, 0));
        practice(961002, USER, Q1, 0, "  ");
        paperAnswer(962001, USER, Q1, 0);
        jdbc.update("UPDATE exam_learning_answer SET user_answer='  ' WHERE id=962001");
        examAnswer(964001, Q1, 0, "  ", "AUTO_GRADED");
        examAnswer(964002, Q2, null, "解题过程", "PENDING");
        CourseOverviewVO empty = overviewService.getOverview(USER, COURSE);
        assertEquals(0, empty.getAnsweredCount());
        assertNull(empty.getLastLearningTime());

        jdbc.update("UPDATE exam_answer SET is_correct=1,grading_status='REVIEWED',reviewed_at=CURRENT_TIMESTAMP "
                + "WHERE id=964002");
        sqlSessionTemplate.clearCache();
        assertEquals(1, overviewService.getOverview(USER, COURSE).getAnsweredCount());
        assertEquals(1, overviewService.getOverview(USER, COURSE).getCorrectCount());
        jdbc.update("UPDATE exam_answer SET is_correct=0 WHERE id=964002");
        sqlSessionTemplate.clearCache();
        assertEquals(1, overviewService.getOverview(USER, COURSE).getAnsweredCount());
        assertEquals(0, overviewService.getOverview(USER, COURSE).getCorrectCount());
    }

    @Test
    void snapshotIdsSurviveRelinkingRenamingAndDeletionWhileCurrentBacklogUsesCurrentLinks() {
        stageAnswer(965001, Q1, "[{\"id\":" + K1 + ",\"name\":\"旧名称\"},{\"id\":"
                + K1 + ",\"name\":\"旧名称\"}]", 0);
        jdbc.update("DELETE FROM question_knowledge_point WHERE question_id=?", Q1);
        jdbc.update("INSERT INTO question_knowledge_point (question_id,knowledge_point_id) VALUES (?,?)", Q1, K3);
        backlog(USER, Q1, 0, 0);
        assertEquals(1, point(K1).getAnsweredCount());
        assertEquals(0, point(K3).getAnsweredCount());
        assertEquals(1, point(K3).getUnresolvedWrongCount());
        assertEquals(1, point(K3).getDueReviewCount());
        jdbc.update("UPDATE knowledge_point SET name='新名称' WHERE id=?", K1);
        sqlSessionTemplate.clearCache();
        assertEquals("新名称", point(K1).getKnowledgePointName());
        jdbc.update("UPDATE knowledge_point SET deleted=1 WHERE id=?", K1);
        jdbc.update("UPDATE question SET deleted=1 WHERE id=?", Q1);
        sqlSessionTemplate.clearCache();
        CourseKnowledgePointFactVO history = point(K1);
        assertEquals("旧名称", history.getKnowledgePointName());
        assertFalse(history.isAvailable());
        assertFalse(history.isTutorAvailable());
        assertEquals(1, history.getAnsweredCount());
        assertEquals(1, overviewService.getOverview(USER, COURSE).getAnsweredCount());
        assertEquals(0, overviewService.getOverview(USER, COURSE).getUnresolvedWrongCount());
    }

    @Test
    void missingSnapshotsStayUnassociatedAndMultiPointBacklogCountsEachQuestionOnce() {
        stageAnswer(965001, Q1, null, 1);
        stageAnswer(965002, Q1, "[]", 0);
        practice(961001, USER, Q2, 1, "A");
        backlog(USER, Q1, 0, 0);
        backlog(USER, Q2, 1, 0);
        CourseKnowledgePointFactVO unknown = point(null);
        assertEquals("未关联知识点", unknown.getKnowledgePointName());
        assertEquals(3, unknown.getAnsweredCount());
        assertEquals(2, unknown.getCorrectCount());
        assertEquals(1, unknown.getUnresolvedWrongCount());
        assertEquals(1, point(K1).getUnresolvedWrongCount());
        assertEquals(1, point(K2).getUnresolvedWrongCount());
        assertEquals(2, overviewService.getOverview(USER, COURSE).getUnresolvedWrongCount());
        assertEquals(2, overviewService.getOverview(USER, COURSE).getDueReviewCount());
    }

    @Test
    void filtersOtherUsersPrivateContentResolvedWrongQuestionsAndFutureOrDeletedReviews() {
        long foreignPrivate = Q2 + 1;
        long ownPrivate = Q2 + 2;
        question(foreignPrivate, "PRIVATE", OTHER);
        question(ownPrivate, "PRIVATE", USER);
        practice(961001, OTHER, Q1, 1, "A");
        practice(961002, USER, foreignPrivate, 1, "A");
        practice(961003, USER, ownPrivate, 0, "B");
        paperAnswer(962001, OTHER, Q1, 1);
        stageAnswer(965001, Q1, "[{\"id\":" + K1 + ",\"name\":\"他人快照\"}]", 1);
        jdbc.update("UPDATE course_stage_assessment SET user_id=? WHERE id=965001", OTHER);
        backlog(OTHER, Q1, 0, 0);
        backlog(USER, foreignPrivate, 0, 0);
        backlog(USER, ownPrivate, 0, 1);
        backlog(USER, Q1, 2, -1);
        backlog(USER, Q2, 0, 0);
        jdbc.update("UPDATE question_review_schedule SET deleted=1 WHERE user_id=? AND question_id=?", USER, Q2);
        jdbc.update("UPDATE wrong_question SET deleted=1 WHERE user_id=? AND question_id=?", USER, Q2);

        CourseOverviewVO overview = overviewService.getOverview(USER, COURSE);

        assertEquals(1, overview.getAnsweredCount());
        assertEquals(0, overview.getCorrectCount());
        assertEquals(1, overview.getUnresolvedWrongCount());
        assertEquals(1, overview.getDueReviewCount());
        assertEquals(0, point(K1).getAnsweredCount());
        assertEquals(1, point(null).getAnsweredCount());
        assertThrows(BusinessException.class, () -> overviewService.getKnowledgePointFacts(USER + 10, COURSE, 1, 10));
    }

    @Test
    void emptyDataAndStablePaginationDoNotRequireLoadingEveryRecord() {
        CourseOverviewVO empty = overviewService.getOverview(USER, COURSE + 90);
        assertEquals(0, empty.getAnsweredCount());
        assertNull(empty.getLastLearningTime());
        assertTrue(overviewService.getKnowledgePointFacts(USER, COURSE + 90, 1, 10).getRecords().isEmpty());
        var first = overviewService.getKnowledgePointFacts(USER, COURSE, 1, 2);
        var second = overviewService.getKnowledgePointFacts(USER, COURSE, 2, 2);
        assertEquals(3, first.getTotal());
        assertEquals(List.of(K1, K2), first.getRecords().stream().map(CourseKnowledgePointFactVO::getKnowledgePointId).toList());
        assertEquals(List.of(K3), second.getRecords().stream().map(CourseKnowledgePointFactVO::getKnowledgePointId).toList());
        assertTrue(overviewService.getKnowledgePointFacts(USER, COURSE, Integer.MAX_VALUE, 50).getRecords().isEmpty());
    }

    @Test
    void tutorProgressAggregatesSessionsAndOnlyOffersReviewedCurrentContent() {
        jdbc.update("""
                INSERT INTO tutor_content (id,knowledge_point_id,content_key,content_version,review_status,title,lesson_json,check_json)
                VALUES (963099,?,'course-facts-reviewed',1,'REVIEWED','知识点教学','{}','{}')
                """, K1);
        tutorAnswer(963001, 0);
        tutorAnswer(963002, 1);
        assertTrue(point(K1).isTutorAvailable());
        assertFalse(point(K2).isTutorAvailable());
        var progress = overviewService.getOverview(USER, COURSE).getTutorProgress();
        assertEquals(1, progress.size());
        assertEquals("COMPLETED", progress.get(0).getStatus());
        assertEquals(2, point(K1).getAnsweredCount());
    }

    private CourseKnowledgePointFactVO point(Long id) {
        return overviewService.getKnowledgePointFacts(USER, COURSE, 1, 50).getRecords().stream()
                .filter(point -> Objects.equals(id, point.getKnowledgePointId())).findFirst().orElseThrow();
    }

    private void question(long id, String visibility, Long owner) {
        jdbc.update("INSERT INTO question (id,content,question_type,course_id,visibility,owner_user_id) "
                + "VALUES (?,'事实测试题','SINGLE_CHOICE',?,?,?)", id, COURSE, visibility, owner);
    }

    private Question eventQuestion(long id) {
        Question question = new Question();
        question.setId(id);
        question.setCourseId(COURSE);
        return question;
    }

    private void practice(long id, long userId, long questionId, int correct, String answer) {
        jdbc.update("INSERT INTO practice_record (id,user_id,question_id,user_answer,is_correct) VALUES (?,?,?,?,?)",
                id, userId, questionId, answer, correct);
    }

    private void paperAnswer(long id, long userId, long questionId, Integer correct) {
        jdbc.update("INSERT INTO exam_learning_session (id,user_id,exam_paper_id) VALUES (?,?,1)", id, userId);
        jdbc.update("INSERT INTO exam_learning_answer (id,session_id,question_id,attempt_no,user_answer,is_correct) "
                + "VALUES (?,?,?,1,'A',?)", id, id, questionId, correct);
    }

    private void tutorAnswer(long id, int correct) {
        jdbc.update("""
                INSERT INTO tutor_session (id,session_key,user_id,course_id,knowledge_point_id,tutor_content_id,
                    check_answer,check_correct,check_answered_at)
                VALUES (?,UUID(),?,?,?,963099,'A',?,CURRENT_TIMESTAMP)
                """, id, USER, COURSE, K1, correct);
    }

    private void examAnswer(long id, long questionId, Integer correct, String answer, String gradingStatus) {
        jdbc.update("INSERT INTO exam_record (id,user_id,exam_paper_id,start_time,end_time,status) "
                + "VALUES (?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)", id, USER);
        jdbc.update("INSERT INTO exam_answer (id,exam_record_id,question_id,user_answer,is_correct,grading_status) "
                + "VALUES (?,?,?,?,?,?)", id, id, questionId, answer, correct, gradingStatus);
    }

    private void stageAnswer(long id, long questionId, String snapshot, int correct) {
        jdbc.update("""
                INSERT INTO course_stage_assessment (id,user_id,course_id,status,selection_strategy,question_count,
                    correct_count,active_session_key,complete_time)
                VALUES (?,?,?,'COMPLETED','COURSE_SEQUENCE_FALLBACK',1,?,NULL,CURRENT_TIMESTAMP)
                """, id, USER, COURSE, correct);
        jdbc.update("""
                INSERT INTO course_stage_assessment_question (id,assessment_id,question_id,sort_order,question_type,
                    knowledge_points_json,content_snapshot,options_snapshot,correct_answer_snapshot,score,
                    user_answer,is_correct,answered_time)
                VALUES (?,?,?,1,'SINGLE_CHOICE',?,'历史题目','[]','A',1,'A',?,CURRENT_TIMESTAMP)
                """, id, id, questionId, snapshot, correct);
    }

    private void backlog(long userId, long questionId, int mastery, int daysUntilDue) {
        jdbc.update("INSERT INTO wrong_question (user_id,question_id,mastery_level) VALUES (?,?,?)",
                userId, questionId, mastery);
        jdbc.update("INSERT INTO question_review_schedule (user_id,question_id,next_review_date) "
                + "VALUES (?,?,DATE_ADD(CURRENT_DATE, INTERVAL ? DAY))", userId, questionId, daysUntilDue);
    }
}
