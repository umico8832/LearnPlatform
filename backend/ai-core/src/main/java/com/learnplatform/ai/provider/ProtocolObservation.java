package com.learnplatform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnplatform.ai.model.JsonContract;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelResult;

final class ProtocolObservation {
    private String model;
    private String responseId;
    private ModelResult.Finish finish;
    private ModelResult.Usage usage;

    synchronized void observe(String data, boolean stream) {
        if ("[DONE]".equals(data)) {
            return;
        }
        JsonNode root;
        try {
            root = JsonContract.parse(data);
        } catch (ModelException exception) {
            throw new ModelException(ModelException.Code.PROTOCOL, partial());
        }
        if (!root.isObject() || root.hasNonNull("error")) {
            throw new ModelException(ModelException.Code.PROTOCOL, partial());
        }
        if (root.path("model").isTextual()) {
            model = root.path("model").asText();
            if (model.isBlank() || model.length() > 100) {
                throw new ModelException(ModelException.Code.PROTOCOL, partial());
            }
        }
        if (root.path("id").isTextual()) {
            responseId = root.path("id").asText();
            if (responseId.length() > 200) {
                throw new ModelException(ModelException.Code.PROTOCOL, partial());
            }
        }
        JsonNode tokens = root.path("usage");
        if (tokens.isObject()) {
            usage = new ModelResult.Usage(integer(tokens.get("prompt_tokens")),
                    integer(tokens.get("completion_tokens")), integer(tokens.get("total_tokens")));
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() > 1 || !stream && choices.size() != 1) {
            throw new ModelException(ModelException.Code.PROTOCOL, partial());
        }
        JsonNode choice = choices.path(0);
        JsonNode refusal = choice.path(stream ? "delta" : "message").path("refusal");
        if (refusal.isTextual() && !refusal.asText().isBlank()) {
            finish = ModelResult.Finish.REFUSAL;
        }
        if (finish != ModelResult.Finish.REFUSAL && choice.hasNonNull("finish_reason")) {
            finish = switch (choice.path("finish_reason").asText()) {
                case "stop" -> ModelResult.Finish.STOP;
                case "tool_calls" -> ModelResult.Finish.TOOL_CALLS;
                case "length" -> ModelResult.Finish.LENGTH;
                case "content_filter" -> ModelResult.Finish.REFUSAL;
                default -> throw new ModelException(ModelException.Code.PROTOCOL, partial());
            };
        }
    }

    private Integer integer(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < 0) {
            throw new ModelException(ModelException.Code.PROTOCOL, partial());
        }
        return node.intValue();
    }

    synchronized ModelResult partial() {
        return new ModelResult(null, java.util.List.of(), model, responseId, finish, usage);
    }

    synchronized void requireTerminal() {
        if (finish == null) {
            throw new ModelException(ModelException.Code.PROTOCOL, partial());
        }
    }
}
