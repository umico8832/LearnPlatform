package com.learnplatform.ai.provider;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.JsonContract;
import com.learnplatform.ai.model.ModelEvent;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class CloudChatModel {
    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;
    private final boolean includeUsage;
    private final boolean toolsSupported;
    private final boolean schemaSupported;

    public CloudChatModel(String baseUrl, String apiKey, Duration timeout, boolean includeUsage,
                          boolean toolsSupported, boolean schemaSupported) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.includeUsage = includeUsage;
        this.toolsSupported = toolsSupported;
        this.schemaSupported = schemaSupported;
    }

    public void validate(ModelRequest request) {
        if (apiKey == null || apiKey.isBlank() || timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new ModelException(ModelException.Code.CONFIGURATION);
        }
        try {
            var endpoint = java.net.URI.create(baseUrl);
            if (!List.of("https", "http").contains(endpoint.getScheme()) || endpoint.getHost() == null
                    || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                    || endpoint.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new ModelException(ModelException.Code.CONFIGURATION);
        }
        boolean usesTools = !request.tools().isEmpty() || request.messages().stream()
                .anyMatch(message -> !message.toolCalls().isEmpty() || message.role() == ModelRequest.Role.TOOL);
        if (!toolsSupported && usesTools || !schemaSupported && request.outputSchema() != null) {
            throw new ModelException(ModelException.Code.UNSUPPORTED);
        }
    }

    public ModelResult complete(ModelRequest request, Cancellation cancellation) {
        validate(request);
        cancellation.check();
        var observation = new ProtocolObservation();
        try {
            var model = OpenAiChatModel.builder().baseUrl(baseUrl).apiKey(apiKey).timeout(timeout).maxRetries(0)
                    .strictJsonSchema(true).httpClientBuilder(new ObservedHttpClientBuilder(observation,
                            cancellation, includeUsage)).build();
            return result(request, model.chat(LangChainRequestMapper.map(request)), observation);
        } catch (RuntimeException exception) {
            return failureOrRefusal(exception, observation);
        }
    }

    public ModelResult stream(ModelRequest request, Consumer<ModelEvent> consumer, Cancellation parentCancellation) {
        Cancellation cancellation = parentCancellation.child();
        validate(request);
        cancellation.check();
        var observation = new ProtocolObservation();
        var future = new CompletableFuture<ModelResult>();
        var handler = new StreamingChatResponseHandler() {
            @Override public synchronized void onPartialResponse(String text) {
                if (!future.isDone() && !cancellation.isCancelled() && request.outputSchema() == null) {
                    try {
                        consumer.accept(new ModelEvent.TextDelta(text));
                    } catch (RuntimeException exception) {
                        cancellation.cancel();
                        future.completeExceptionally(new ModelException(ModelException.Code.CANCELLED,
                                observation.partial()));
                    }
                }
            }
            @Override public synchronized void onCompleteResponse(ChatResponse response) {
                if (!future.isDone()) {
                    try {
                        cancellation.check();
                        future.complete(result(request, response, observation));
                    } catch (RuntimeException exception) {
                        future.completeExceptionally(exception);
                    }
                }
            }
            @Override public synchronized void onError(Throwable error) {
                try {
                    future.complete(failureOrRefusal(error, observation));
                } catch (RuntimeException exception) {
                    future.completeExceptionally(exception);
                }
            }
        };
        try {
            OpenAiStreamingChatModel.builder().baseUrl(baseUrl).apiKey(apiKey).timeout(timeout).strictJsonSchema(true)
                    .httpClientBuilder(new ObservedHttpClientBuilder(observation, cancellation, includeUsage))
                    .build().chat(LangChainRequestMapper.map(request), handler);
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                cancellation.check();
                try {
                    ModelResult result = future.get(100, TimeUnit.MILLISECONDS);
                    cancellation.check();
                    consumer.accept(new ModelEvent.Completed(result));
                    return result;
                } catch (TimeoutException exception) {
                    // Polling keeps cancellation responsive even before the first upstream event.
                }
            }
            throw new ModelException(ModelException.Code.TIMEOUT, observation.partial());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelException(ModelException.Code.CANCELLED, observation.partial());
        } catch (ExecutionException exception) {
            return failureOrRefusal(exception.getCause(), observation);
        } finally {
            cancellation.cancel();
            future.cancel(false);
        }
    }

    private ModelResult result(ModelRequest request, ChatResponse response, ProtocolObservation observation) {
        observation.requireTerminal();
        ModelResult metadata = observation.partial();
        var calls = response.aiMessage().toolExecutionRequests().stream().map(call -> new ModelRequest.ToolCall(
                call.id(), call.name(), call.arguments())).toList();
        var result = new ModelResult(response.aiMessage().text(), calls, metadata.model(), metadata.responseId(),
                metadata.finish(), metadata.usage());
        if (result.finish() == ModelResult.Finish.REFUSAL || result.finish() == ModelResult.Finish.LENGTH) {
            return result;
        }
        try {
            if (result.finish() == ModelResult.Finish.TOOL_CALLS) {
                if (calls.isEmpty()
                        || calls.stream().map(ModelRequest.ToolCall::id).distinct().count() != calls.size()) {
                    throw new ModelException(ModelException.Code.PROTOCOL);
                }
                for (var call : calls) {
                    var tool = request.tools().stream().filter(item -> item.name().equals(call.name())).findFirst()
                            .orElseThrow(() -> new ModelException(ModelException.Code.PROTOCOL));
                    JsonContract.validate(call.arguments(), tool.parametersJson());
                }
            } else {
                result.requireCompleteText();
                if (request.outputSchema() != null) {
                    JsonContract.validate(result.text(), request.outputSchema().json());
                }
            }
            return result;
        } catch (ModelException exception) {
            throw new ModelException(exception.code(), result);
        }
    }

    private ModelResult failureOrRefusal(Throwable error, ProtocolObservation observation) {
        if (error instanceof ModelException modelException) {
            throw modelException;
        }
        ModelResult partial = observation.partial();
        if (partial.finish() == ModelResult.Finish.REFUSAL) {
            return new ModelResult(null, List.of(), partial.model(), partial.responseId(),
                    partial.finish(), partial.usage());
        }
        throw new ModelException(partial.finish() == null ? ModelException.Code.UPSTREAM
                : ModelException.Code.PROTOCOL, partial);
    }
}
