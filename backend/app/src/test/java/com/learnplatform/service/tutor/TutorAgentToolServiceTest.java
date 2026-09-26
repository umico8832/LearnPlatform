package com.learnplatform.service.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.TutorSessionVO;
import com.learnplatform.dto.TutorCheckResultVO;
import com.learnplatform.dto.TutorLearningContextVO;
import com.learnplatform.service.KnowledgeSearchService;
import com.learnplatform.service.TutorSessionService;
import com.learnplatform.service.knowledge.KnowledgeSearchResult;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TutorAgentToolServiceTest {
    private final TutorSessionService sessions = mock(TutorSessionService.class);
    private final KnowledgeSearchService knowledge = mock(KnowledgeSearchService.class);
    private final ObjectMapper json = new ObjectMapper();
    private TutorAgentToolService tools;

    @BeforeEach void setUp() {
        tools = new TutorAgentToolService(sessions, json, knowledge);
    }

    @Test void returnsOnlyReviewedPublicLessonFields() throws Exception {
        when(sessions.get(7L, 10L, "session")).thenReturn(session());

        String result = tools.execute(7L, 10L, "session",
                new ModelRequest.ToolCall("call", "read_tutor_lesson", "{}"));

        JsonNode root = json.readTree(result);
        assertEquals("ArrayStack", root.path("title").asText());
        assertEquals("先搬移再写入", root.path("lesson").path("summary").asText());
        assertFalse(root.path("check").has("correctOptionId"));
        assertFalse(root.path("check").has("correctExplanation"));
        assertFalse(root.path("check").has("incorrectExplanation"));
    }

    @Test void returnsThePersistedLearningEvidenceSnapshot() throws Exception {
        TutorSessionVO session = session();
        TutorLearningContextVO context = new TutorLearningContextVO();
        context.setPaperAnswerCount(3);
        session.setLearningContext(context);
        when(sessions.get(7L, 10L, "session")).thenReturn(session);

        String result = tools.execute(7L, 10L, "session",
                new ModelRequest.ToolCall("call", "read_learning_evidence", "{}"));

        assertEquals(3, json.readTree(result).path("paperAnswerCount").asInt());
    }

    @Test void rejectsOtherUsersAndUnknownOrNonEmptyTools() {
        when(sessions.get(8L, 10L, "session")).thenThrow(new BusinessException(ResultCode.NOT_FOUND));
        assertThrows(BusinessException.class, () -> tools.execute(8L, 10L, "session",
                new ModelRequest.ToolCall("call", "read_tutor_lesson", "{}")));

        when(sessions.get(7L, 10L, "session")).thenReturn(session());
        assertEquals(ModelException.Code.PROTOCOL, assertThrows(ModelException.class,
                () -> tools.execute(7L, 10L, "session",
                        new ModelRequest.ToolCall("call", "delete_course", "{}"))).code());
        assertEquals(ModelException.Code.SCHEMA, assertThrows(ModelException.class,
                () -> tools.execute(7L, 10L, "session",
                        new ModelRequest.ToolCall("call", "read_tutor_lesson", "{\"courseId\":10}"))).code());
    }

    @Test void searchUsesBoundCourseAndRunAndRejectsResourceOverrides() {
        when(knowledge.enabled()).thenReturn(true);
        when(sessions.get(7L, 10L, "session")).thenReturn(session());
        UUID run = UUID.randomUUID();
        when(knowledge.search(7L, 10L, "栈", run)).thenReturn(new KnowledgeSearchResult(List.of()));
        assertEquals("{\"citations\":[]}", tools.execute(7L, 10L, "session", new ModelRequest.ToolCall(
                "search", "search_course_knowledge", "{\"query\":\"栈\"}"), run));
        assertThrows(ModelException.class, () -> tools.execute(7L, 10L, "session", new ModelRequest.ToolCall(
                "search", "search_course_knowledge", "{\"query\":\"栈\",\"courseId\":20}"), run));
    }

    @Test void offersOnlyAnActionAndDoesNotGradeAnUnansweredCheck() throws Exception {
        when(sessions.get(7L, 10L, "session")).thenReturn(session());
        JsonNode result = json.readTree(tools.execute(7L, 10L, "session",
                new ModelRequest.ToolCall("check", "present_tutor_check", "{}")));
        assertEquals("UNANSWERED", result.path("status").asText());
        assertEquals("CHECK", result.path("action").path("type").asText());
        assertFalse(result.has("result"));
        verify(sessions, never()).answer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test void readsFreshServerOutcomeWithoutExposingTheSelectedOptionOrOfferingAnotherAttempt() throws Exception {
        TutorSessionVO session = session();
        session.setCheckAnswer("LEFT");
        TutorCheckResultVO outcome = new TutorCheckResultVO();
        outcome.setCorrect(false);
        outcome.setExplanation("回看搬移方向");
        session.setCheckResult(outcome);
        when(sessions.get(7L, 10L, "session")).thenReturn(session);
        for (String name : List.of("present_tutor_check", "read_tutor_check_result")) {
            JsonNode result = json.readTree(tools.execute(7L, 10L, "session",
                    new ModelRequest.ToolCall("result", name, "{}")));
            assertEquals("ANSWERED", result.path("status").asText());
            assertFalse(result.path("result").path("correct").asBoolean());
            assertEquals("回看搬移方向", result.path("result").path("explanation").asText());
            assertFalse(result.has("action"));
            assertFalse(result.toString().contains("LEFT"));
        }
    }

    @Test void rejectsFabricatedAnswersAndResourceArgumentsForTeachingTools() {
        for (String name : List.of("present_tutor_check", "read_tutor_check_result")) {
            assertEquals(ModelException.Code.SCHEMA, assertThrows(ModelException.class,
                    () -> tools.execute(7L, 10L, "session", new ModelRequest.ToolCall("action", name,
                            "{\"optionId\":\"RIGHT\",\"correct\":true}"))).code());
        }
    }

    private TutorSessionVO session() {
        TutorSessionVO session = new TutorSessionVO();
        session.setTitle("ArrayStack");
        session.setLesson(com.learnplatform.ai.model.JsonContract.parse("{\"summary\":\"先搬移再写入\"}"));
        session.setCheck(com.learnplatform.ai.model.JsonContract.parse("{\"prompt\":\"向哪边搬？\"}"));
        return session;
    }
}
