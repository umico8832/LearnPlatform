package com.learnplatform.service.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnplatform.ai.model.JsonContract;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.dto.TutorSessionVO;
import com.learnplatform.service.KnowledgeSearchService;
import com.learnplatform.service.TutorSessionService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TutorAgentToolService implements TutorAgentToolExecutor {
    private final TutorSessionService sessions;
    private final ObjectMapper json;
    private final KnowledgeSearchService knowledge;

    public TutorAgentToolService(TutorSessionService sessions, ObjectMapper json, KnowledgeSearchService knowledge) {
        this.sessions = sessions;
        this.json = json;
        this.knowledge = knowledge;
    }

    @Override
    public boolean supportsKnowledgeSearch() {
        return knowledge.enabled();
    }

    @Override
    public String execute(Long userId, Long courseId, String sessionKey, ModelRequest.ToolCall call) {
        return execute(userId, courseId, sessionKey, call, null);
    }

    @Override
    public String execute(Long userId, Long courseId, String sessionKey, ModelRequest.ToolCall call, UUID runId) {
        if (!"search_course_knowledge".equals(call.name())) {
            requireEmptyArguments(call.arguments());
        }
        TutorSessionVO session = sessions.get(userId, courseId, sessionKey);
        return switch (call.name()) {
            case "read_tutor_lesson" -> lesson(session);
            case "read_learning_evidence" -> write(json.valueToTree(session.getLearningContext()));
            case "present_tutor_check" -> check(session, true);
            case "read_tutor_check_result" -> check(session, false);
            case "search_course_knowledge" -> search(userId, courseId, call.arguments(), runId);
            default -> throw new ModelException(ModelException.Code.PROTOCOL);
        };
    }

    private String search(Long userId, Long courseId, String arguments, UUID runId) {
        if (!supportsKnowledgeSearch()) {
            throw new ModelException(ModelException.Code.UNSUPPORTED);
        }
        JsonContract.validate(arguments, TutorAgentRuntime.KNOWLEDGE_SEARCH_SCHEMA);
        String query = JsonContract.parse(arguments).path("query").textValue();
        return write(json.valueToTree(knowledge.search(userId, courseId, query, runId)));
    }

    private String lesson(TutorSessionVO session) {
        ObjectNode result = json.createObjectNode();
        result.put("title", session.getTitle());
        result.set("lesson", session.getLesson());
        result.set("check", session.getCheck());
        return write(result);
    }

    private String check(TutorSessionVO session, boolean present) {
        ObjectNode result = json.createObjectNode();
        if (session.getCheckResult() == null) {
            result.put("status", "UNANSWERED");
            if (present) {
                result.putObject("action").put("type", "CHECK");
            }
        } else {
            result.put("status", "ANSWERED");
            result.set("result", json.valueToTree(session.getCheckResult()));
        }
        return write(result);
    }

    private void requireEmptyArguments(String arguments) {
        JsonNode node = JsonContract.parse(arguments);
        if (!node.isObject() || !node.isEmpty()) {
            throw new ModelException(ModelException.Code.SCHEMA);
        }
    }

    private String write(JsonNode value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Tutor Agent 工具结果无法序列化", exception);
        }
    }
}
