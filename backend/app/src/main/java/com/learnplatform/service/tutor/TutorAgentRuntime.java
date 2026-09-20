package com.learnplatform.service.tutor;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.service.AiInvocationService;
import com.learnplatform.service.ai.AiCallContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TutorAgentRuntime {
    private static final int HISTORY_LIMIT = 12;
    private static final int MAX_TOOL_ROUNDS = 3;
    private static final String SYSTEM_PROMPT = """
            你是 LearnPlatform 的课程 Tutor。你只能依据当前会话中已审查的教学内容回答，
            不得把用户自评当作掌握事实，不得泄露理解检查的正确选项，也不得声称执行了未注册操作。
            每个用户问题都必须先调用 read_tutor_lesson；需要结合服务端学习记录时再调用
            read_learning_evidence。工具参数必须是空对象。信息不足时明确说明边界，不要自行补造课程事实。
            回答应直接、简洁，并在回答后等待用户继续提问。
            """;
    private static final String EMPTY_OBJECT_SCHEMA = """
            {"type":"object","properties":{},"required":[],"additionalProperties":false}
            """;
    private static final List<ModelRequest.Tool> TOOLS = List.of(
            new ModelRequest.Tool("read_tutor_lesson", "读取当前会话已审查的教学内容与公开检查题。",
                    EMPTY_OBJECT_SCHEMA),
            new ModelRequest.Tool("read_learning_evidence", "读取当前会话启动时固化的学习证据计数与时间。",
                    EMPTY_OBJECT_SCHEMA));

    private final AiInvocationService invocation;
    private final TutorAgentToolExecutor tools;

    public TutorAgentRuntime(AiInvocationService invocation, TutorAgentToolExecutor tools) {
        this.invocation = invocation;
        this.tools = tools;
    }

    public String respond(Long userId, Long courseId, String sessionKey, UUID runId,
                          List<TutorAgentHistoryMessage> history, String question) {
        List<ModelRequest.Message> messages = initialMessages(history, question);
        boolean readLesson = false;
        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            ModelRequest request = new ModelRequest(messages, invocation.defaultOptions(), TOOLS, null);
            boolean lessonWasRead = readLesson;
            boolean finalToolRound = round == MAX_TOOL_ROUNDS;
            ModelResult result = invocation.generate(new AiCallContext(userId, "tutor_agent", runId),
                    request, new Cancellation(), candidate -> validate(candidate, lessonWasRead, finalToolRound));
            if (result.finish() != ModelResult.Finish.TOOL_CALLS) {
                return result.requireCompleteText();
            }
            messages.add(new ModelRequest.Message(ModelRequest.Role.ASSISTANT, result.text(),
                    result.toolCalls(), null));
            for (ModelRequest.ToolCall call : result.toolCalls()) {
                String output = tools.execute(userId, courseId, sessionKey, call);
                messages.add(new ModelRequest.Message(ModelRequest.Role.TOOL, output, List.of(), call.id()));
                readLesson |= "read_tutor_lesson".equals(call.name());
            }
        }
        throw new ModelException(ModelException.Code.PROTOCOL);
    }

    private ModelResult validate(ModelResult result, boolean readLesson, boolean finalToolRound) {
        if ((result.finish() == ModelResult.Finish.TOOL_CALLS && finalToolRound)
                || (result.finish() != ModelResult.Finish.TOOL_CALLS && !readLesson)) {
            throw new ModelException(ModelException.Code.PROTOCOL, result);
        }
        return result;
    }

    private List<ModelRequest.Message> initialMessages(List<TutorAgentHistoryMessage> history, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Tutor Agent question is required");
        }
        List<ModelRequest.Message> messages = new ArrayList<>();
        messages.add(ModelRequest.Message.text(ModelRequest.Role.SYSTEM, SYSTEM_PROMPT));
        int from = Math.max(0, history.size() - HISTORY_LIMIT);
        history.subList(from, history.size()).forEach(message -> messages.add(
                ModelRequest.Message.text(message.role(), message.content())));
        messages.add(ModelRequest.Message.text(ModelRequest.Role.USER, question));
        return messages;
    }
}
