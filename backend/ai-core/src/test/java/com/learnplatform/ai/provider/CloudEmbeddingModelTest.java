package com.learnplatform.ai.provider;

import com.sun.net.httpserver.HttpServer;
import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.ai.model.JsonContract;
import com.learnplatform.ai.model.ModelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CloudEmbeddingModelTest {
    private HttpServer server;
    private String response = """
            {"model":"actual","data":[{"index":0,"embedding":[0.1,0.2]}],
             "usage":{"prompt_tokens":2,"total_tokens":2}}
            """;
    private int status = 200;
    private CountDownLatch release = new CountDownLatch(0);
    private final CountDownLatch arrived = new CountDownLatch(1);
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final EmbeddingRequest request = new EmbeddingRequest("requested", List.of("栈与队列"), 2);

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            requests.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            arrived.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach void stop() {
        release.countDown();
        server.stop(0);
    }

    @Test void sendsExplicitFloatEncodingAndDimensions() {
        var result = model(Duration.ofSeconds(2)).embed(request, new Cancellation());
        assertEquals(List.of(List.of(0.1, 0.2)), result.vectors());
        var sent = JsonContract.parse(body.get());
        assertEquals("float", sent.path("encoding_format").asText());
        assertEquals("requested", sent.path("model").asText());
        assertEquals("栈与队列", sent.path("input").get(0).asText());
        assertEquals(2, sent.path("dimensions").asInt());
        assertEquals(1, requests.get());
    }

    @Test void leavesNativeDimensionsToModelWhenNotConfigured() {
        model(Duration.ofSeconds(2)).embed(new EmbeddingRequest("requested", List.of("a"), null), new Cancellation());
        assertFalse(JsonContract.parse(body.get()).has("dimensions"));
    }

    @Test void upstreamFailureIsSanitizedAndNeverRetried() {
        status = 429;
        response = "upstream-private-response";
        var error = assertThrows(ModelException.class, () -> model(Duration.ofSeconds(2)).embed(request, new Cancellation()));
        assertEquals(ModelException.Code.UPSTREAM, error.code());
        assertEquals("AI_UPSTREAM", error.getMessage());
        assertNull(error.getCause());
        assertEquals(1, requests.get());
    }

    @Test void cancellationBeforeRequestDoesNotAccessUpstream() {
        var cancellation = new Cancellation();
        cancellation.cancel();
        assertEquals(ModelException.Code.CANCELLED, assertThrows(ModelException.class,
                () -> model(Duration.ofSeconds(2)).embed(request, cancellation)).code());
        assertEquals(0, requests.get());
    }

    @Test void cancelsWhileWaitingForUpstream() throws Exception {
        release = new CountDownLatch(1);
        var cancellation = new Cancellation();
        var result = CompletableFuture.supplyAsync(() -> assertThrows(ModelException.class,
                () -> model(Duration.ofSeconds(5)).embed(request, cancellation)));
        assertTrue(arrived.await(2, TimeUnit.SECONDS));
        cancellation.cancel();
        assertEquals(ModelException.Code.CANCELLED, result.get(1, TimeUnit.SECONDS).code());
    }

    @Test void enforcesRequestTimeout() {
        release = new CountDownLatch(1);
        assertEquals(ModelException.Code.TIMEOUT, assertThrows(ModelException.class,
                () -> model(Duration.ofMillis(150)).embed(request, new Cancellation())).code());
    }

    @Test void rejectsInvalidConfigurationBeforeNetwork() {
        var model = new CloudEmbeddingModel("https://example.test/v1?secret=hidden", "test-key", Duration.ofSeconds(2));
        assertEquals(ModelException.Code.CONFIGURATION, assertThrows(ModelException.class,
                () -> model.embed(request, new Cancellation())).code());
        assertEquals(0, requests.get());
    }

    @Test void rejectsInvalidRequestAndDoesNotPrintInput() {
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingRequest("model", List.of(""), null));
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingRequest("model", List.of("a"), 0));
        assertFalse(request.toString().contains("栈与队列"));
    }

    private CloudEmbeddingModel model(Duration timeout) {
        return new CloudEmbeddingModel("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/", "test-key", timeout);
    }
}
