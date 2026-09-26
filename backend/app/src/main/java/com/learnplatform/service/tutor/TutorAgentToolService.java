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
    private final TutorAgentPracticeService practice;

    public TutorAgentToolService(TutorSessionService sessions, ObjectMapper json, KnowledgeSearchService knowledge,
                                 TutorAgentPracticeService practice) {
        this.sessions = sessions;
        this.json = json;
        this.knowledge = knowledge;
        this.practice = practice;
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
            case "request_tutor_hint" -> hintAvailability(session);
            case "recommend_tutor_practice" -> recommendPractice(userId, courseId, sessionKey);
            case "read_tutor_practice_result" -> practiceResult(userId, courseId, sessionKey, runId);
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

    private String hintAvailability(TutorSessionVO session) {
        ObjectNode result = json.createObjectNode();
        if (session.getCheckResult() == null) {
            result.put("status", "AVAILABLE");
        } else {
            result.put("status", "ANSWERED");
            result.set("result", json.valueToTree(session.getCheckResult()));
        }
        return write(result);
    }

    private String recommendPractice(Long userId, Long courseId, String sessionKey) {
        Long questionId = practice.recommend(userId, courseId, sessionKey);
        ObjectNode result = json.createObjectNode();
        result.put("status", questionId == null ? "UNAVAILABLE" : "AVAILABLE");
        if (questionId != null) {
            result.putObject("action").put("type", "PRACTICE").put("questionId", questionId);
        }
        return write(result);
    }

    private String practiceResult(Long userId, Long courseId, String sessionKey, UUID runId) {
        if (runId == null) { throw new ModelException(ModelException.Code.PROTOCOL); }
        var outcome = practice.latestResult(userId, courseId, sessionKey, runId.toString());
        ObjectNode result = json.createObjectNode();
        result.put("status", outcome == null ? "UNANSWERED" : "ANSWERED");
        if (outcome != null) {
            result.putObject("result").put("correct", outcome.getCorrect())
                    .put("explanation", outcome.getAnalysis());
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
