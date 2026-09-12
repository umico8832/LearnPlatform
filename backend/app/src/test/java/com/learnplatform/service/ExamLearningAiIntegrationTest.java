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
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
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

        org.mockito.Mockito.when(aiProvider.defaultOptions()).thenReturn(
                new com.learnplatform.ai.model.ModelRequest.Options("test-model", 2000, 0.7));
        StringBuilder upstreamContext = new StringBuilder();
        doAnswer(invocation -> {
            com.learnplatform.ai.model.ModelRequest request = invocation.getArgument(0);
            upstreamContext.append(request.messages().get(1).content());
            Consumer<com.learnplatform.ai.model.ModelEvent> consumer = invocation.getArgument(1);
            consumer.accept(new com.learnplatform.ai.model.ModelEvent.TextDelta("基于真实错答的辅导"));
            return new com.learnplatform.ai.model.ModelResult("基于真实错答的辅导", java.util.List.of(),
                    "test-model", null, com.learnplatform.ai.model.ModelResult.Finish.STOP, null);
        }).when(aiProvider).stream(any(), any(), any());

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

    @Test
    void subjectiveLearningAnswerKeepsAiAssistanceSnapshotUngraded() {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM user WHERE username = 'testuser'", Long.class);
        Long courseId = jdbcTemplate.queryForObject(
                "SELECT id FROM course WHERE content_key = 'cs408-data-structures'", Long.class);
        Long paperId = jdbcTemplate.queryForObject("""
                SELECT id FROM exam_paper WHERE title = '2026 年 408 真题·数据结构部分'
                """, Long.class);
        Long questionId = jdbcTemplate.queryForObject("""
                SELECT eq.question_id FROM exam_question eq
                JOIN question q ON q.id = eq.question_id
                WHERE eq.exam_paper_id = ? AND q.question_type = 'SHORT_ANSWER'
                ORDER BY eq.sort_order LIMIT 1
                """, Long.class, paperId);
        jdbcTemplate.update("INSERT IGNORE INTO user_course (user_id, course_id) VALUES (?, ?)", userId, courseId);

        ExamLearningSessionVO session = learningService.startSession(paperId, userId);
        ExamLearningAnswerRequest answer = new ExamLearningAnswerRequest();
        answer.setQuestionId(questionId);
        answer.setUserAnswer("使用中序遍历维护最小差值");
        learningService.submitAnswer(session.getId(), answer, userId);

        org.mockito.Mockito.when(aiProvider.defaultOptions()).thenReturn(
                new com.learnplatform.ai.model.ModelRequest.Options("test-model", 2000, 0.7));
        StringBuilder upstreamContext = new StringBuilder();
        stubStreamResponse(upstreamContext, "主观题辅导");
        learningAiService.streamAssistance(session.getId(), questionId, "explanation", userId, ignored -> { });

        assertTrue(upstreamContext.toString().contains("结果：未判分"));
        Integer interactionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM exam_learning_ai_interaction
                WHERE learning_session_id = ? AND question_id = ?
                  AND answer_correct IS NULL AND interaction_type = 'EXPLANATION' AND status = 1
                """, Integer.class, session.getId(), questionId);
        assertEquals(1, interactionCount);
    }

    @Test
    void correctObjectiveLearningAnswerKeepsAiAssistanceSnapshotCorrect() {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM user WHERE username = 'testuser'", Long.class);
        Long courseId = jdbcTemplate.queryForObject(
                "SELECT id FROM course WHERE content_key = 'cs408-data-structures'", Long.class);
        Long paperId = jdbcTemplate.queryForObject(
                "SELECT id FROM exam_paper WHERE title = '2026 年 408 真题·数据结构选择题'", Long.class);
        jdbcTemplate.update("INSERT IGNORE INTO user_course (user_id, course_id) VALUES (?, ?)", userId, courseId);

        ExamLearningSessionVO session = learningService.startSession(paperId, userId);
        Long questionId = session.getQuestions().get(0).getQuestionId();
        String correctAnswer = jdbcTemplate.queryForObject("""
                SELECT option_label FROM question_option
                WHERE question_id = ? AND is_correct = 1
                ORDER BY sort_order LIMIT 1
                """, String.class, questionId);
        ExamLearningAnswerRequest answer = new ExamLearningAnswerRequest();
        answer.setQuestionId(questionId);
        answer.setUserAnswer(correctAnswer);
        learningService.submitAnswer(session.getId(), answer, userId);

        org.mockito.Mockito.when(aiProvider.defaultOptions()).thenReturn(
                new com.learnplatform.ai.model.ModelRequest.Options("test-model", 2000, 0.7));
        StringBuilder upstreamContext = new StringBuilder();
        stubStreamResponse(upstreamContext, "客观题辅导");
        learningAiService.streamAssistance(session.getId(), questionId, "explanation", userId, ignored -> { });

        assertTrue(upstreamContext.toString().contains("结果：正确"));
        Integer interactionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM exam_learning_ai_interaction
                WHERE learning_session_id = ? AND question_id = ?
                  AND answer_correct = 1 AND interaction_type = 'EXPLANATION' AND status = 1
                """, Integer.class, session.getId(), questionId);
        assertEquals(1, interactionCount);
    }

    private void stubStreamResponse(StringBuilder upstreamContext, String response) {
        doAnswer(invocation -> {
            com.learnplatform.ai.model.ModelRequest request = invocation.getArgument(0);
            upstreamContext.append(request.messages().get(1).content());
            Consumer<com.learnplatform.ai.model.ModelEvent> consumer = invocation.getArgument(1);
            consumer.accept(new com.learnplatform.ai.model.ModelEvent.TextDelta(response));
            return new com.learnplatform.ai.model.ModelResult(response, java.util.List.of(),
                    "test-model", null, com.learnplatform.ai.model.ModelResult.Finish.STOP, null);
        }).when(aiProvider).stream(any(), any(), any());
    }
}
