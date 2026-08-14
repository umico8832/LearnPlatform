package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.ExamLearningAnswerRequest;
import com.learnplatform.dto.ExamLearningSessionVO;
import com.learnplatform.dto.TutorSessionVO;
import com.learnplatform.service.ai.AiProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class ExamLearningAiIntegrationTest extends IntegrationTestBase {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ExamPaperLearningService learningService;
    @Autowired private ExamLearningAiService learningAiService;
    @Autowired private TutorSessionService tutorSessionService;
    @MockBean private AiProvider aiProvider;

    @Test
    void real2026PaperAnswerCanProduceTraceableCourseAiAssistance() {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM user WHERE username = 'testuser'", Long.class);
        Long courseId = jdbcTemplate.queryForObject(
                "SELECT id FROM course WHERE content_key = 'cs408-data-structures'", Long.class);
        Long paperId = jdbcTemplate.queryForObject(
                "SELECT id FROM exam_paper WHERE title = '2026 年 408 真题·数据结构选择题'", Long.class);
        jdbcTemplate.update("INSERT IGNORE INTO user_course (user_id, course_id) VALUES (?, ?)", userId, courseId);

        ExamLearningSessionVO session = learningService.startSession(paperId, userId);
        Long questionId = session.getQuestions().get(0).getQuestionId();
        ExamLearningAnswerRequest answer = new ExamLearningAnswerRequest();
        answer.setQuestionId(questionId);
        answer.setUserAnswer("B");
        learningService.submitAnswer(session.getId(), answer, userId);

        StringBuilder upstreamContext = new StringBuilder();
        doAnswer(invocation -> {
            upstreamContext.append(invocation.getArgument(1, String.class));
            Consumer<String> consumer = invocation.getArgument(2);
            consumer.accept("基于真实错答的辅导");
            return null;
        }).when(aiProvider).chatStream(any(), any(), any());

        StringBuilder output = new StringBuilder();
        learningAiService.streamAssistance(
                session.getId(), questionId, "explanation", userId, output::append);

        assertEquals("基于真实错答的辅导", output.toString());
        assertTrue(upstreamContext.toString().contains("2026 年 408 真题·数据结构选择题"));
        assertTrue(upstreamContext.toString().contains("用户最近答案：B"));
        assertTrue(upstreamContext.toString().contains("结果：错误"));
        Integer interactionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM exam_learning_ai_interaction
                WHERE user_id = ? AND course_id = ? AND exam_paper_id = ?
                  AND learning_session_id = ? AND question_id = ?
                  AND answer_correct = 0 AND interaction_type = 'EXPLANATION' AND status = 1
                """, Integer.class, userId, courseId, paperId, session.getId(), questionId);
        Integer eventCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM course_learning_event
                WHERE user_id = ? AND course_id = ? AND subject_id = ?
                  AND event_type = 'PAPER_LEARNING_AI_ASSISTED'
                  AND event_source = 'PAPER_LEARNING_AI'
                """, Integer.class, userId, courseId, questionId);
        assertEquals(1, interactionCount);
        assertEquals(1, eventCount);

        Long mappedKnowledgePointId = jdbcTemplate.queryForObject("""
                SELECT knowledge_point_id FROM question_knowledge_point
                WHERE question_id = ? ORDER BY id LIMIT 1
                """, Long.class, questionId);
        Long tutorKnowledgePointId = jdbcTemplate.queryForObject("""
                WITH RECURSIVE descendants AS (
                    SELECT id FROM knowledge_point WHERE id = ? AND course_id = ?
                    UNION ALL
                    SELECT kp.id FROM knowledge_point kp
                    JOIN descendants parent ON kp.parent_id = parent.id
                    WHERE kp.course_id = ? AND kp.deleted = 0
                )
                SELECT descendants.id FROM descendants
                JOIN tutor_content ON tutor_content.knowledge_point_id = descendants.id
                WHERE tutor_content.review_status = 'REVIEWED'
                ORDER BY tutor_content.id LIMIT 1
                """, Long.class, mappedKnowledgePointId, courseId, courseId);
        TutorSessionVO tutor = tutorSessionService.start(userId, courseId, tutorKnowledgePointId);

        assertEquals(1, tutor.getLearningContext().getPaperAnswerCount());
        assertEquals(1, tutor.getLearningContext().getPaperIncorrectCount());
        assertEquals(1, tutor.getLearningContext().getPaperAiAssistanceCount());
        assertEquals(1, tutor.getLearningContext().getUnresolvedWrongCount());
        Integer snapshotPaperAnswers = jdbcTemplate.queryForObject("""
                SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(learning_context_json, '$.paperAnswerCount')) AS UNSIGNED)
                FROM tutor_session WHERE session_key = ?
                """, Integer.class, tutor.getSessionKey());
        assertEquals(1, snapshotPaperAnswers);
    }
}
