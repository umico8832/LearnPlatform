package com.learnplatform.ai.provider;

import com.sun.net.httpserver.HttpServer;
import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.JsonContract;
import com.learnplatform.ai.model.ModelEvent;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CloudChatModelTest {
    private static final String SCHEMA = """
            {"type":"object","properties":{"courseId":{"type":"integer","minimum":1}},
             "required":["courseId"],"additionalProperties":false}
            """;
    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private String response;
    private String contentType = "application/json";
    private int status = 200;
    private long delayMillis;
    private final ModelRequest.Options options = new ModelRequest.Options("test-model", 200, 0.7);

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            if (delayMillis > 0) {
                try { Thread.sleep(delayMillis); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            }
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", contentType);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void synchronousRequestPreservesOptionsAndExactUsage() {
        response = completion("\"lesson\"", "stop", "{\"prompt_tokens\":12,\"total_tokens\":20}");
        ModelResult result = model(true).complete(textRequest(), new Cancellation());
        assertEquals("lesson", result.requireCompleteText());
        assertEquals("actual-model", result.model());
        assertEquals(12, result.usage().inputTokens());
        assertNull(result.usage().outputTokens());
        var sent = JsonContract.parse(body.get());
        assertEquals(200, sent.path("max_tokens").asInt());
        assertEquals("test-model", sent.path("model").asText());
        assertEquals("system", sent.path("messages").get(0).path("content").asText());
    }

    @Test void unknownUsageIsNotEstimated() {
        response = completion("\"lesson\"", "stop", "null");
        assertNull(model(true).complete(textRequest(), new Cancellation()).usage());
    }

    @Test void toolCallingPreservesIdsAndSupportsTheNextTurn() {
        response = """
                {"id":"r1","model":"actual-model","choices":[{"finish_reason":"tool_calls","message":{
                 "role":"assistant","content":null,"tool_calls":[{"id":"call1","type":"function",
                 "function":{"name":"course_facts","arguments":"{\\"courseId\\":7}"}}]}}]}
                """;
        var tool = new ModelRequest.Tool("course_facts", "Read the authorized course facts", SCHEMA);
        var request = new ModelRequest(textRequest().messages(), options, List.of(tool), null);
        var result = model(true).complete(request, new Cancellation());
        assertEquals(ModelResult.Finish.TOOL_CALLS, result.finish());
        assertEquals("call1", result.toolCalls().getFirst().id());
        var messages = new ArrayList<>(request.messages());
        messages.add(new ModelRequest.Message(ModelRequest.Role.ASSISTANT, null, result.toolCalls(), null));
        messages.add(new ModelRequest.Message(ModelRequest.Role.TOOL, "{\"attempts\":3}", List.of(), "call1"));
        response = completion("\"based on three attempts\"", "stop", "null");
        model(true).complete(new ModelRequest(messages, options, List.of(tool), null), new Cancellation());
        var sent = JsonContract.parse(body.get());
        assertEquals("call1", sent.path("messages").get(3).path("tool_call_id").asText());
        assertEquals(4, sent.path("messages").size());
    }

    @Test void rejectsInvalidToolArgumentsBeforeAnyExecution() {
        response = """
                {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","tool_calls":[
                {"id":"call1","type":"function","function":{"name":"course_facts","arguments":"{\\"courseId\\":-1}"}}
                ]}}]}
                """;
        var request = new ModelRequest(textRequest().messages(), options,
                List.of(new ModelRequest.Tool("course_facts", "Course facts", SCHEMA)), null);
        assertEquals(ModelException.Code.SCHEMA, assertThrows(ModelException.class,
                () -> model(true).complete(request, new Cancellation())).code());
    }

    @Test void validatesStructuredOutputLocallyAndRetainsBilledUsageOnFailure() {
        var request = new ModelRequest(textRequest().messages(), options, List.of(),
                new ModelRequest.Schema("course", SCHEMA));
        response = completion("\"{\\\"courseId\\\":7}\"", "stop", "null");
        assertEquals("{\"courseId\":7}", model(true).complete(request, new Cancellation()).text());
        assertEquals("json_schema", JsonContract.parse(body.get()).path("response_format").path("type").asText());
        response = completion("\"{\\\"courseId\\\":0}\"", "stop", "{\"prompt_tokens\":12,\"total_tokens\":20}");
        var error = assertThrows(ModelException.class, () -> model(true).complete(request, new Cancellation()));
        assertEquals(ModelException.Code.SCHEMA, error.code());
        assertEquals(20, error.partialResult().usage().totalTokens());
    }

    @Test void streamsContentAndTerminalUsageWithoutLosingFinishReason() {
        contentType = "text/event-stream";
        response = """
                data: {"model":"actual-model","choices":[{"index":0,"delta":{"content":"hello"}}]}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: {"choices":[],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}

                data: [DONE]

                """;
        var events = new ArrayList<ModelEvent>();
        var cancellation = new Cancellation();
        var result = model(false).stream(textRequest(), events::add, cancellation);
        assertFalse(cancellation.isCancelled());
        assertEquals("hello", result.requireCompleteText());
        assertEquals(5, result.usage().totalTokens());
        assertEquals(List.of(new ModelEvent.TextDelta("hello"), new ModelEvent.Completed(result)), events);
        assertFalse(JsonContract.parse(body.get()).has("stream_options"));
    }

    @Test void assemblesToolArgumentDeltasOnlyAfterTerminalEvent() {
        contentType = "text/event-stream";
        response = """
                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call1","type":"function","function":{"name":"course_facts","arguments":"{\\"courseId\\":"}}]}}]}

                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"7}"}}]}}]}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

                data: [DONE]

                """;
        var request = new ModelRequest(textRequest().messages(), options,
                List.of(new ModelRequest.Tool("course_facts", "Read course facts", SCHEMA)), null);
        var events = new ArrayList<ModelEvent>();
        var result = model(true).stream(request, events::add, new Cancellation());
        assertEquals("{\"courseId\":7}", result.toolCalls().getFirst().arguments());
        assertEquals(List.of(new ModelEvent.Completed(result)), events);
    }

    @Test void structuredStreamDoesNotExposeUnvalidatedJsonFragments() {
        contentType = "text/event-stream";
        response = """
                data: {"choices":[{"index":0,"delta":{"content":"{\\"courseId\\":0}"}}]}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """;
        var request = new ModelRequest(textRequest().messages(), options, List.of(),
                new ModelRequest.Schema("course", SCHEMA));
        var events = new ArrayList<ModelEvent>();
        assertThrows(ModelException.class, () -> model(true).stream(request, events::add, new Cancellation()));
        assertTrue(events.isEmpty());
    }

    @Test void cancellationAfterFirstDeltaStopsCompletion() {
        contentType = "text/event-stream";
        response = """
                data: {"choices":[{"index":0,"delta":{"content":"partial"}}]}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """;
        var cancellation = new Cancellation();
        var events = new ArrayList<ModelEvent>();
        assertThrows(ModelException.class, () -> model(true).stream(textRequest(), event -> {
            events.add(event);
            cancellation.cancel();
        }, cancellation));
        assertEquals(List.of(new ModelEvent.TextDelta("partial")), events);
    }

    @Test void timeoutIsClassifiedAndDoesNotRetry() {
        delayMillis = 600;
        response = completion("\"late\"", "stop", "null");
        var model = new CloudChatModel(url(), "test-placeholder", Duration.ofMillis(150), true, true, true);
        assertEquals(ModelException.Code.TIMEOUT, assertThrows(ModelException.class,
                () -> model.complete(textRequest(), new Cancellation())).code());
        assertEquals(1, requests.get());
    }

    @Test void rejectsEofWithoutFinishEvenIfDoneMarkerWasSent() {
        contentType = "text/event-stream";
        response = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"partial\"}}]}\n\n"
                + "data: [DONE]\n\n";
        var events = new ArrayList<ModelEvent>();
        assertThrows(ModelException.class, () -> model(true).stream(textRequest(), events::add, new Cancellation()));
        assertTrue(events.stream().noneMatch(ModelEvent.Completed.class::isInstance));
    }

    @Test void refusesAndTruncatesExplicitly() {
        response = completion("\"partial\"", "length", "null");
        var result = model(true).complete(textRequest(), new Cancellation());
        assertEquals(ModelResult.Finish.LENGTH, result.finish());
        assertEquals(ModelException.Code.TRUNCATED,
                assertThrows(ModelException.class, result::requireCompleteText).code());
        response = """
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","refusal":"private upstream text"}}],
                "usage":{"prompt_tokens":4,"completion_tokens":1,"total_tokens":5}}
                """;
        result = model(true).complete(textRequest(), new Cancellation());
        assertEquals(ModelResult.Finish.REFUSAL, result.finish());
        assertNull(result.text());
        assertEquals(5, result.usage().totalTokens());
    }

    @Test void doesNotRetryOrExposeUpstreamBodies() {
        status = 500;
        response = "private-upstream-text test-placeholder";
        var error = assertThrows(ModelException.class, () -> model(true).complete(textRequest(), new Cancellation()));
        assertFalse(error.toString().contains("private-upstream-text"));
        assertNull(error.getCause());
        assertEquals(1, requests.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a URL", "https://example.invalid/v1?key=test-placeholder",
            "https://test-placeholder@example.invalid/v1"})
    void invalidEndpointIsRejectedBeforeAdmission(String endpoint) {
        var model = new CloudChatModel(endpoint, "test-placeholder", Duration.ofSeconds(3), true, true, true);
        var error = assertThrows(ModelException.class, () -> model.validate(textRequest()));
        assertEquals(ModelException.Code.CONFIGURATION, error.code());
        assertFalse(error.toString().contains("test-placeholder"));
        assertEquals(0, requests.get());
    }

    @Test void cancelledAndUnsupportedRequestsNeverReachCloud() {
        var cancellation = new Cancellation();
        cancellation.cancel();
        assertEquals(ModelException.Code.CANCELLED, assertThrows(ModelException.class,
                () -> model(true).complete(textRequest(), cancellation)).code());
        var limited = new CloudChatModel(url(), "test-placeholder", Duration.ofSeconds(3), true, false, false);
        var request = new ModelRequest(textRequest().messages(), options, List.of(),
                new ModelRequest.Schema("course", SCHEMA));
        assertEquals(ModelException.Code.UNSUPPORTED, assertThrows(ModelException.class,
                () -> limited.complete(request, new Cancellation())).code());
        assertEquals(0, requests.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"courseId\":1,\"courseId\":2}", "{\"courseId\":1} {}", "{\"courseId\":\"1\"}",
            "{}", "{\"courseId\":1,\"private\":true}"})
    void strictContractRejectsAmbiguousAndInvalidJson(String value) {
        assertThrows(ModelException.class, () -> JsonContract.validate(value, SCHEMA));
    }

    @Test void rejectsOrphanToolResultsAndRemoteSchemas() {
        assertThrows(IllegalArgumentException.class, () -> new ModelRequest(List.of(
                new ModelRequest.Message(ModelRequest.Role.TOOL, "result", List.of(), "unknown")), options, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new ModelRequest.Schema("external",
                "{\"type\":\"object\",\"$ref\":\"https://example.invalid/schema\"}"));
    }

    private ModelRequest textRequest() { return ModelRequest.text("system", "user", options); }
    private String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1"; }
    private CloudChatModel model(boolean includeUsage) {
        return new CloudChatModel(url(), "test-placeholder", Duration.ofSeconds(3), includeUsage, true, true);
    }
    private String completion(String content, String finish, String usage) {
        return "{\"id\":\"response1\",\"model\":\"actual-model\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + content + "},\"finish_reason\":\"" + finish + "\"}],\"usage\":" + usage + "}";
    }
}
