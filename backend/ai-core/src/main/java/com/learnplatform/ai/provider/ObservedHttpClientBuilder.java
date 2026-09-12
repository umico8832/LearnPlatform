package com.learnplatform.ai.provider;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.JsonContract;
import com.learnplatform.ai.model.ModelException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

import java.time.Duration;

final class ObservedHttpClientBuilder implements HttpClientBuilder {
    private final HttpClientBuilder delegate = JdkHttpClient.builder();
    private final ProtocolObservation observation;
    private final Cancellation cancellation;
    private final boolean includeUsage;

    ObservedHttpClientBuilder(ProtocolObservation observation, Cancellation cancellation, boolean includeUsage) {
        this.observation = observation;
        this.cancellation = cancellation;
        this.includeUsage = includeUsage;
    }

    @Override public Duration connectTimeout() { return delegate.connectTimeout(); }
    @Override public Duration readTimeout() { return delegate.readTimeout(); }
    @Override public HttpClientBuilder connectTimeout(Duration timeout) {
        delegate.connectTimeout(timeout);
        return this;
    }
    @Override public HttpClientBuilder readTimeout(Duration timeout) {
        delegate.readTimeout(timeout);
        return this;
    }

    @Override
    public HttpClient build() {
        HttpClient client = delegate.build();
        return new HttpClient() {
            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                try {
                    cancellation.check();
                    SuccessfulHttpResponse response = client.execute(request);
                    observation.observe(response.body(), false);
                    observation.requireTerminal();
                    cancellation.check();
                    return response;
                } catch (RuntimeException exception) {
                    throw safe(exception);
                }
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                try {
                    cancellation.check();
                    client.execute(streamRequest(request), parser, new ServerSentEventListener() {
                        private boolean failed;
                        @Override public void onOpen(SuccessfulHttpResponse response) { listener.onOpen(response); }
                        @Override public synchronized void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                            if (failed || cancellation.isCancelled()) {
                                context.parsingHandle().cancel();
                                onError(new ModelException(ModelException.Code.CANCELLED));
                                return;
                            }
                            try {
                                if (event.data() != null && !event.data().isBlank()) {
                                    observation.observe(event.data(), true);
                                }
                                listener.onEvent(event, context);
                            } catch (RuntimeException exception) {
                                context.parsingHandle().cancel();
                                onError(exception);
                            }
                        }
                        @Override public synchronized void onClose() {
                            if (!failed) {
                                try {
                                    observation.requireTerminal();
                                    listener.onClose();
                                } catch (RuntimeException exception) {
                                    onError(exception);
                                }
                            }
                        }
                        @Override public synchronized void onError(Throwable error) {
                            if (!failed) {
                                failed = true;
                                listener.onError(safe(error));
                            }
                        }
                    });
                } catch (RuntimeException exception) {
                    listener.onError(safe(exception));
                }
            }
        };
    }

    private HttpRequest streamRequest(HttpRequest request) {
        if (includeUsage) {
            return request;
        }
        ObjectNode body = (ObjectNode) JsonContract.parse(request.body());
        body.remove("stream_options");
        return HttpRequest.builder().method(request.method()).url(request.url()).headers(request.headers())
                .body(body.toString()).build();
    }

    private ModelException safe(Throwable error) {
        if (error instanceof ModelException modelException) {
            return modelException.partialResult() != null ? modelException
                    : new ModelException(modelException.code(), observation.partial());
        }
        ModelException.Code code = ModelException.Code.UPSTREAM;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.http.HttpTimeoutException
                    || cause instanceof java.net.SocketTimeoutException) {
                code = ModelException.Code.TIMEOUT;
            }
        }
        return new ModelException(code, observation.partial());
    }
}
