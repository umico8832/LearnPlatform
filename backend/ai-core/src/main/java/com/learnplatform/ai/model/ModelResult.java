package com.learnplatform.ai.model;

import java.util.List;

public record ModelResult(String text, List<ModelRequest.ToolCall> toolCalls, String model,
                          String responseId, Finish finish, Usage usage) {
    public ModelResult {
        toolCalls = List.copyOf(toolCalls);
    }

    public enum Finish { STOP, TOOL_CALLS, LENGTH, REFUSAL }

    public record Usage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        public Usage {
            if (negative(inputTokens) || negative(outputTokens) || negative(totalTokens)) {
                throw new IllegalArgumentException("Negative token usage");
            }
        }
        private static boolean negative(Integer value) {
            return value != null && value < 0;
        }
    }

    public String requireCompleteText() {
        if (finish != Finish.STOP || text == null || text.isBlank() || !toolCalls.isEmpty()) {
            throw new ModelException(finish == Finish.REFUSAL ? ModelException.Code.REFUSAL
                    : finish == Finish.LENGTH ? ModelException.Code.TRUNCATED : ModelException.Code.PROTOCOL, this);
        }
        return text;
    }
}
