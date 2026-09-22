package com.learnplatform.service.knowledge.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QdrantKnowledgeVectorStoreTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void createsCollectionAndSendsIsolatedUpsertSearchAndDeleteRequests() throws Exception {
        AtomicReference<JsonNode> upsert = new AtomicReference<>();
        AtomicReference<JsonNode> query = new AtomicReference<>();
        AtomicReference<JsonNode> delete = new AtomicReference<>();
        AtomicReference<JsonNode> create = new AtomicReference<>();
        server = server(exchange -> {
            if (exchange.getRequestMethod().equals("GET")) {
                respond(exchange, 404, "{}");
            } else if (exchange.getRequestMethod().equals("PUT")
                    && exchange.getRequestURI().getPath().endsWith("/points")) {
                upsert.set(request(exchange));
                respond(exchange, 200, "{\"status\":\"ok\",\"result\":{\"status\":\"completed\"}}");
            } else if (exchange.getRequestMethod().equals("PUT")) {
                create.set(request(exchange));
                respond(exchange, 200, "{\"status\":\"ok\",\"result\":{\"status\":\"completed\"}}");
            } else if (exchange.getRequestURI().getPath().endsWith("/query")) {
                query.set(request(exchange));
                respond(exchange, 200,
                        "{\"result\":{\"points\":[{\"score\":0.9,\"payload\":{\"bundle_id\":7,\"index_key\":\""
                                + key() + "\",\"chunk_id\":\"chunk-1\"}}]}}");
            } else {
                delete.set(request(exchange));
                respond(exchange, 200, "{\"status\":\"ok\",\"result\":{\"status\":\"completed\"}}");
            }
        });
        QdrantKnowledgeVectorStore store = store();

        store.ensureCollection(3);
        store.upsert(7L, key(), List.of(new QdrantKnowledgeVectorStore.Point("chunk-1",
                List.of(1D, 2D, 3D))));
        List<QdrantKnowledgeVectorStore.Match> matches = store.search(7L, key(), List.of(1D, 2D, 3D), 4);
        store.delete(7L, key());

        assertEquals("Cosine", create.get().path("vectors").path("distance").asText());
        assertEquals(7L, upsert.get().path("points").get(0).path("payload").path("bundle_id").asLong());
        assertEquals(3, upsert.get().path("points").get(0).path("payload").size());
        assertFalse(upsert.get().toString().contains("text"));
        assertEquals(2, query.get().path("filter").path("must").size());
        assertTrueMatch(matches);
        assertEquals(2, delete.get().path("filter").path("must").size());
    }

    @Test
    void rejectsExistingCollectionWithDifferentDistanceOrDimensions() throws Exception {
        server = server(exchange -> respond(exchange, 200,
                "{\"result\":{\"config\":{\"params\":{\"vectors\":{\"size\":4,\"distance\":\"Dot\"}}}}}"));

        assertThrows(IllegalStateException.class, () -> store().ensureCollection(3));
    }

    @Test
    void rejectsCrossScopeSearchResultsAndDoesNotLeakHttpErrors() throws Exception {
        server = server(exchange -> {
            if (exchange.getRequestMethod().equals("GET")) {
                respond(exchange, 200,
                        "{\"result\":{\"config\":{\"params\":{\"vectors\":{\"size\":3,\"distance\":\"Cosine\"}}}}}");
            } else if (exchange.getRequestURI().getPath().endsWith("/query")) {
                respond(exchange, 200,
                        "{\"result\":{\"points\":[{\"score\":0.9,\"payload\":{\"bundle_id\":8,\"index_key\":\""
                                + key() + "\",\"chunk_id\":\"chunk-1\"}}]}}");
            } else respond(exchange, 500, "sensitive upstream detail");
        });

        QdrantKnowledgeVectorStore store = store();
        store.ensureCollection(3);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> store.search(7L, key(), List.of(1D, 2D, 3D), 4));
        assertFalse(error.getMessage().contains("sensitive"));
    }

    @Test
    void sanitizesHttpFailuresAndTimesOutWhileReadingAResponseBody() throws Exception {
        server = server(exchange -> respond(exchange, 500, "sensitive upstream detail"));
        IllegalStateException httpError = assertThrows(IllegalStateException.class,
                () -> store().ensureCollection(3));
        assertFalse(httpError.getMessage().contains("sensitive"));
        stopServer();

        server = server(exchange -> {
            exchange.sendResponseHeaders(200, 2);
            try {
                Thread.sleep(150);
                exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        QdrantKnowledgeVectorStore slowStore = new QdrantKnowledgeVectorStore(
                "http://localhost:" + server.getAddress().getPort(), null, "knowledge_chunks", Duration.ofMillis(25));

        assertThrows(IllegalStateException.class, () -> slowStore.ensureCollection(3));
        slowStore.close();
    }

    private QdrantKnowledgeVectorStore store() {
        return new QdrantKnowledgeVectorStore("http://localhost:" + server.getAddress().getPort(), null,
                "knowledge_chunks", Duration.ofSeconds(2));
    }

    private HttpServer server(Handler handler) throws IOException {
        HttpServer result = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        result.createContext("/", exchange -> handler.handle(exchange));
        result.start();
        return result;
    }

    private JsonNode request(HttpExchange exchange) throws IOException {
        return JSON.readTree(exchange.getRequestBody().readAllBytes());
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void assertTrueMatch(List<QdrantKnowledgeVectorStore.Match> matches) {
        assertEquals(List.of(new QdrantKnowledgeVectorStore.Match("chunk-1", 0.9D)), matches);
    }

    private String key() {
        return "a".repeat(64);
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
