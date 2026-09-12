package com.learnplatform.ai.provider;

import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.JsonContract;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

final class LangChainRequestMapper {
    private LangChainRequestMapper() { }

    static ChatRequest map(ModelRequest request) {
        var builder = ChatRequest.builder().messages(request.messages().stream().map(message -> map(message, request))
                .toList()).modelName(request.options().model()).maxOutputTokens(request.options().maxOutputTokens())
                .temperature(request.options().temperature());
        if (!request.tools().isEmpty()) {
            builder.toolSpecifications(request.tools().stream().map(tool -> ToolSpecification.builder()
                    .name(tool.name()).description(tool.description())
                    .parameters(toolSchema(tool.parametersJson())).build()).toList());
        }
        if (request.outputSchema() != null) {
            builder.responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON)
                    .jsonSchema(JsonSchema.builder().name(request.outputSchema().name())
                            .rootElement(JsonRawSchema.from(request.outputSchema().json())).build()).build());
        }
        return builder.build();
    }

    private static JsonObjectSchema toolSchema(String json) {
        var root = JsonContract.parse(json);
        var builder = JsonObjectSchema.builder();
        root.path("properties").fields().forEachRemaining(field ->
                builder.addProperty(field.getKey(), JsonRawSchema.from(field.getValue().toString())));
        var required = new java.util.ArrayList<String>();
        root.path("required").forEach(node -> required.add(node.asText()));
        return builder.required(required)
                .additionalProperties(root.path("additionalProperties").asBoolean(false)).build();
    }

    private static ChatMessage map(ModelRequest.Message message, ModelRequest request) {
        return switch (message.role()) {
            case SYSTEM -> SystemMessage.from(message.content());
            case USER -> UserMessage.from(message.content());
            case ASSISTANT -> message.toolCalls().isEmpty() ? AiMessage.from(message.content())
                    : AiMessage.from(message.content(), message.toolCalls().stream().map(call -> ToolExecutionRequest
                            .builder().id(call.id()).name(call.name()).arguments(call.arguments()).build()).toList());
            case TOOL -> ToolExecutionResultMessage.from(message.toolCallId(), request.messages().stream()
                    .flatMap(item -> item.toolCalls().stream()).filter(call -> call.id().equals(message.toolCallId()))
                    .findFirst().orElseThrow().name(), message.content());
        };
    }
}
