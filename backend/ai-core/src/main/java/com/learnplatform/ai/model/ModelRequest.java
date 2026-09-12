package com.learnplatform.ai.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record ModelRequest(List<Message> messages, Options options, List<Tool> tools, Schema outputSchema) {
    public ModelRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        Objects.requireNonNull(options);
        if (messages.isEmpty() || tools.stream().map(Tool::name).distinct().count() != tools.size()) {
            throw new IllegalArgumentException("Messages must be present and tool names must be unique");
        }
        var pending = new HashSet<String>();
        var usedIds = new HashSet<String>();
        for (Message message : messages) {
            if (message.role() == Role.TOOL) {
                if (!pending.remove(message.toolCallId())) {
                    throw new IllegalArgumentException("Unmatched tool result");
                }
            } else {
                if (!pending.isEmpty()) {
                    throw new IllegalArgumentException("Missing tool results");
                }
                for (ToolCall call : message.toolCalls()) {
                    if (!usedIds.add(call.id())) {
                        throw new IllegalArgumentException("Duplicate tool call ID");
                    }
                    pending.add(call.id());
                }
            }
        }
        if (!pending.isEmpty()) {
            throw new IllegalArgumentException("Missing tool results");
        }
    }

    public static ModelRequest text(String system, String user, Options options) {
        return new ModelRequest(List.of(Message.text(Role.SYSTEM, system), Message.text(Role.USER, user)),
                options, List.of(), null);
    }

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public record Options(String model, Integer maxOutputTokens, Double temperature) {
        public Options {
            requireText(model);
            if (maxOutputTokens == null || maxOutputTokens < 1
                    || temperature != null && (!Double.isFinite(temperature) || temperature < 0 || temperature > 2)) {
                throw new IllegalArgumentException("Invalid model options");
            }
        }
    }

    public record Message(Role role, String content, List<ToolCall> toolCalls, String toolCallId) {
        public Message {
            Objects.requireNonNull(role);
            toolCalls = List.copyOf(toolCalls);
            if (role == Role.TOOL) {
                requireText(toolCallId);
                Objects.requireNonNull(content);
            } else if (toolCallId != null) {
                throw new IllegalArgumentException("Only tool results have a tool call ID");
            }
            if (role != Role.ASSISTANT && !toolCalls.isEmpty()) {
                throw new IllegalArgumentException("Only assistant messages may request tools");
            }
            if (toolCalls.isEmpty() && role != Role.TOOL) {
                requireText(content);
            }
        }
        public static Message text(Role role, String content) {
            return new Message(role, content, List.of(), null);
        }
    }

    public record ToolCall(String id, String name, String arguments) {
        public ToolCall {
            requireText(id);
            requireName(name);
            requireText(arguments);
        }
    }

    public record Tool(String name, String description, String parametersJson) {
        public Tool {
            requireName(name);
            requireText(description);
            JsonContract.validateSchema(parametersJson);
            var root = JsonContract.parse(parametersJson);
            root.fieldNames().forEachRemaining(key -> {
                if (!List.of("type", "properties", "required", "additionalProperties", "description").contains(key)) {
                    throw new IllegalArgumentException("Unsupported tool schema keyword");
                }
            });
            if (!root.path("properties").isObject() || !root.path("additionalProperties").isBoolean()) {
                throw new IllegalArgumentException("Tool schema requires properties and explicit additionalProperties");
            }
        }
    }

    public record Schema(String name, String json) {
        public Schema {
            requireName(name);
            JsonContract.validateSchema(json);
        }
    }

    private static void requireName(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid schema or tool name");
        }
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required text is missing");
        }
    }
}
