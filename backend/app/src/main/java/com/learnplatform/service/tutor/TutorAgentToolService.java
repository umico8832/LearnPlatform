package com.learnplatform.service.tutor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnplatform.ai.model.JsonContract;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.TutorContent;
import com.learnplatform.entity.TutorSession;
import com.learnplatform.mapper.TutorContentMapper;
import com.learnplatform.mapper.TutorSessionMapper;
import org.springframework.stereotype.Service;

@Service
public class TutorAgentToolService implements TutorAgentToolExecutor {
    private final TutorSessionMapper sessions;
    private final TutorContentMapper contents;
    private final ObjectMapper json;

    public TutorAgentToolService(TutorSessionMapper sessions, TutorContentMapper contents, ObjectMapper json) {
        this.sessions = sessions;
        this.contents = contents;
        this.json = json;
    }

    @Override
    public String execute(Long userId, Long courseId, String sessionKey, ModelRequest.ToolCall call) {
        requireEmptyArguments(call.arguments());
        TutorSession session = sessions.selectOne(new QueryWrapper<TutorSession>()
                .eq("session_key", sessionKey).eq("user_id", userId).eq("course_id", courseId));
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Tutor 会话不存在");
        }
        return switch (call.name()) {
            case "read_tutor_lesson" -> lesson(session);
            case "read_learning_evidence" -> learningEvidence(session);
            default -> throw new ModelException(ModelException.Code.PROTOCOL);
        };
    }

    private String lesson(TutorSession session) {
        TutorContent content = contents.selectById(session.getTutorContentId());
        if (content == null || !"REVIEWED".equals(content.getReviewStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "已审查 Tutor 教学内容不存在");
        }
        ObjectNode result = json.createObjectNode();
        result.put("title", content.getTitle());
        result.set("lesson", parseObject(content.getLessonJson()));
        ObjectNode check = parseObject(content.getCheckJson()).deepCopy();
        check.remove("correctOptionId");
        check.remove("correctExplanation");
        check.remove("incorrectExplanation");
        result.set("check", check);
        return write(result);
    }

    private String learningEvidence(TutorSession session) {
        String value = session.getLearningContextJson();
        return value == null || value.isBlank() ? "{}" : write(parseObject(value));
    }

    private void requireEmptyArguments(String arguments) {
        JsonNode node = JsonContract.parse(arguments);
        if (!node.isObject() || !node.isEmpty()) {
            throw new ModelException(ModelException.Code.SCHEMA);
        }
    }

    private ObjectNode parseObject(String value) {
        try {
            JsonNode node = json.readTree(value);
            if (node instanceof ObjectNode object) {
                return object;
            }
            throw new IllegalStateException("Tutor 内容必须是 JSON 对象");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("已审查 Tutor 内容格式无效", exception);
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
