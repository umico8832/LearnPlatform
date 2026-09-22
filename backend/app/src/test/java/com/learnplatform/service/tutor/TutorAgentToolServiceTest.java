package com.learnplatform.service.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.TutorContent;
import com.learnplatform.entity.TutorSession;
import com.learnplatform.mapper.TutorContentMapper;
import com.learnplatform.mapper.TutorSessionMapper;
import com.learnplatform.service.KnowledgeSearchService;
import com.learnplatform.service.knowledge.KnowledgeSearchResult;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TutorAgentToolServiceTest {
    private final TutorSessionMapper sessions = mock(TutorSessionMapper.class);
    private final TutorContentMapper contents = mock(TutorContentMapper.class);
    private final ObjectMapper json = new ObjectMapper();
    private TutorAgentToolService tools;

    @BeforeEach void setUp() {
        tools = new TutorAgentToolService(sessions, contents, json);
    }

    @Test void returnsOnlyReviewedPublicLessonFields() throws Exception {
        TutorSession session = session();
        when(sessions.selectOne(any())).thenReturn(session);
        TutorContent content = new TutorContent();
        content.setId(9L);
        content.setReviewStatus("REVIEWED");
        content.setTitle("ArrayStack");
        content.setLessonJson("{\"summary\":\"先搬移再写入\"}");
        content.setCheckJson("{\"prompt\":\"向哪边搬？\",\"correctOptionId\":\"RIGHT\","
                + "\"correctExplanation\":\"正确\",\"incorrectExplanation\":\"错误\"}");
        when(contents.selectById(9L)).thenReturn(content);

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
        TutorSession session = session();
        session.setLearningContextJson("{\"paperAnswerCount\":3,\"unresolvedWrongCount\":1}");
        when(sessions.selectOne(any())).thenReturn(session);

        String result = tools.execute(7L, 10L, "session",
                new ModelRequest.ToolCall("call", "read_learning_evidence", "{}"));

        assertEquals(3, json.readTree(result).path("paperAnswerCount").asInt());
    }

    @Test void rejectsOtherUsersAndUnknownOrNonEmptyTools() {
        when(sessions.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> tools.execute(8L, 10L, "session",
                new ModelRequest.ToolCall("call", "read_tutor_lesson", "{}")));

        when(sessions.selectOne(any())).thenReturn(session());
        assertEquals(ModelException.Code.PROTOCOL, assertThrows(ModelException.class,
                () -> tools.execute(7L, 10L, "session",
                        new ModelRequest.ToolCall("call", "delete_course", "{}"))).code());
        assertEquals(ModelException.Code.SCHEMA, assertThrows(ModelException.class,
                () -> tools.execute(7L, 10L, "session",
                        new ModelRequest.ToolCall("call", "read_tutor_lesson", "{\"courseId\":10}"))).code());
    }

    @Test void searchUsesBoundCourseAndRunAndRejectsResourceOverrides() {
        var knowledge = mock(KnowledgeSearchService.class);
        tools = new TutorAgentToolService(sessions, contents, json, knowledge);
        when(knowledge.enabled()).thenReturn(true);
        when(sessions.selectOne(any())).thenReturn(session());
        UUID run = UUID.randomUUID();
        when(knowledge.search(7L, 10L, "栈", run)).thenReturn(new KnowledgeSearchResult(List.of()));
        assertEquals("{\"citations\":[]}", tools.execute(7L, 10L, "session", new ModelRequest.ToolCall(
                "search", "search_course_knowledge", "{\"query\":\"栈\"}"), run));
        assertThrows(ModelException.class, () -> tools.execute(7L, 10L, "session", new ModelRequest.ToolCall(
                "search", "search_course_knowledge", "{\"query\":\"栈\",\"courseId\":20}"), run));
    }

    private TutorSession session() {
        TutorSession session = new TutorSession();
        session.setId(5L);
        session.setUserId(7L);
        session.setCourseId(10L);
        session.setTutorContentId(9L);
        return session;
    }
}
