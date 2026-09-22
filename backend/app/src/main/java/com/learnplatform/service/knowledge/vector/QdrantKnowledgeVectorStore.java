package com.learnplatform.service.knowledge.vector;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class QdrantKnowledgeVectorStore implements AutoCloseable {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_DIMENSIONS = 65536;
    private static final int MAX_POINTS = 256;
    private static final int MAX_LIMIT = 100;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final URI baseUri;
    private final String apiKey;
    private final String collection;
    private final Duration timeout;
    private final HttpClient client;
    private volatile Integer dimensions;

    public QdrantKnowledgeVectorStore(String baseUrl, String apiKey, String collection, Duration timeout) {
        this.baseUri = baseUri(baseUrl);
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : requireHeaderValue(apiKey);
        this.collection = requireCollection(collection);
        this.timeout = requireTimeout(timeout);
        this.client = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public void ensureCollection(int dimensions) {
        requireDimensions(dimensions);
        HttpResult existing = request("GET", collectionPath(), null);
        if (existing.status == 404) {
            ObjectNode body = JSON.createObjectNode();
            body.putObject("vectors").put("size", dimensions).put("distance", "Cosine");
            requireSuccess(request("PUT", collectionPath(), body));
            this.dimensions = dimensions;
            return;
        }
        verifyCollection(existing, dimensions);
    }

    public void requireCollection(int dimensions) {
        requireDimensions(dimensions);
        verifyCollection(request("GET", collectionPath(), null), dimensions);
    }

    private void verifyCollection(HttpResult existing, int dimensions) {
        requireSuccess(existing);
        JsonNode vectors = existing.body.path("result").path("config").path("params").path("vectors");
        if (!vectors.isObject() || vectors.path("size").asInt(-1) != dimensions
                || !"Cosine".equals(vectors.path("distance").asText())) {
            throw unavailable();
        }
        this.dimensions = dimensions;
    }

    public void upsert(Long bundleId, String indexKey, List<Point> points) {
        validateScope(bundleId, indexKey);
        if (points == null || points.isEmpty() || points.size() > MAX_POINTS) {
            throw invalidInput();
        }
        ObjectNode body = JSON.createObjectNode();
        ArrayNode values = body.putArray("points");
        Set<String> chunkIds = new HashSet<>();
        for (Point point : points) {
            if (point == null || !validChunkId(point.chunkId()) || !chunkIds.add(point.chunkId())) {
                throw invalidInput();
            }
            ObjectNode item = values.addObject();
            item.put("id", pointId(bundleId, indexKey, point.chunkId()));
            ArrayNode vector = item.putArray("vector");
            for (Double value : validVector(point.vector(), requiredDimensions())) {
                vector.add(value.floatValue());
            }
            ObjectNode payload = item.putObject("payload");
            payload.put("bundle_id", bundleId).put("index_key", indexKey).put("chunk_id", point.chunkId());
        }
        requireCompleted(request("PUT", collectionPath() + "/points?wait=true", body));
    }

    public List<Match> search(Long bundleId, String indexKey,
                              List<Double> vector, int limit) {
        validateScope(bundleId, indexKey);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw invalidInput();
        }
        ObjectNode body = JSON.createObjectNode();
        ArrayNode query = body.putArray("query");
        for (Double value : validVector(vector, requiredDimensions())) {
            query.add(value.floatValue());
        }
        body.set("filter", scopeFilter(bundleId, indexKey));
        body.put("limit", limit).put("with_payload", true).put("with_vector", false);
        HttpResult response = request("POST", collectionPath() + "/points/query", body);
        requireSuccess(response);
        JsonNode results = response.body.path("result").path("points");
        if (!results.isArray() || results.size() > limit) {
            throw unavailable();
        }
        List<Match> matches = new ArrayList<>();
        for (JsonNode result : results) {
            JsonNode payload = result.path("payload");
            String chunkId = payload.path("chunk_id").asText(null);
            if (!validChunkId(chunkId) || !payload.path("bundle_id").isIntegralNumber()
                    || !payload.path("bundle_id").canConvertToLong()
                    || payload.path("bundle_id").longValue() != bundleId
                    || !indexKey.equals(payload.path("index_key").asText())
                    || !result.path("score").isNumber()
                    || !Double.isFinite(result.path("score").asDouble())) {
                throw unavailable();
            }
            matches.add(new Match(chunkId, result.path("score").asDouble()));
        }
        if (matches.stream().map(Match::chunkId).distinct().count() != matches.size()) {
            throw unavailable();
        }
        return List.copyOf(matches);
    }

    public void delete(Long bundleId, String indexKey) {
        validateScope(bundleId, indexKey);
        ObjectNode body = JSON.createObjectNode();
        body.set("filter", scopeFilter(bundleId, indexKey));
        requireCompleted(request("POST", collectionPath() + "/points/delete?wait=true", body));
    }

    @Override
    public void close() {
        client.shutdownNow();
    }

    private HttpResult request(String method, String path, JsonNode body) {
        long started = System.nanoTime();
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(timeout)
                    .header("Accept", "application/json");
            if (apiKey != null) {
                request.header("api-key", apiKey);
            }
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body),
                                StandardCharsets.UTF_8));
            }
            HttpResponse<InputStream> response = client.send(request.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream source = response.body()) {
                byte[] bytes = readResponseBody(source, timeout.toNanos() - (System.nanoTime() - started));
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw unavailable();
                }
                JsonNode parsed = bytes.length == 0 ? JSON.createObjectNode() : JSON.readTree(bytes);
                return new HttpResult(response.statusCode(), parsed);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | RuntimeException exception) {
            throw unavailable();
        }
    }

    private byte[] readResponseBody(InputStream source, long remainingNanos) throws IOException, InterruptedException {
        if (remainingNanos <= 0) {
            throw unavailable();
        }
        CompletableFuture<byte[]> body = CompletableFuture.supplyAsync(() -> {
            try {
                return source.readNBytes(MAX_RESPONSE_BYTES + 1);
            } catch (IOException exception) {
                throw new ResponseReadException(exception);
            }
        });
        try {
            return body.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            source.close();
            throw unavailable();
        } catch (ExecutionException exception) {
            throw new IOException("Response body read failed");
        }
    }

    private ObjectNode scopeFilter(Long bundleId, String indexKey) {
        ObjectNode filter = JSON.createObjectNode();
        ArrayNode must = filter.putArray("must");
        must.addObject().put("key", "bundle_id").putObject("match").put("value", bundleId);
        must.addObject().put("key", "index_key").putObject("match").put("value", indexKey);
        return filter;
    }

    private String collectionPath() {
        return "collections/" + collection;
    }

    private static URI baseUri(String value) {
        try {
            URI uri = URI.create(value);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getUserInfo() != null) {
                throw invalidInput();
            }
            String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath() + "/";
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), path.replaceAll("/+", "/"), null, null);
        } catch (Exception exception) {
            throw invalidInput();
        }
    }

    private static String requireCollection(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,255}")) {
            throw invalidInput();
        }
        return value;
    }

    private static Duration requireTimeout(Duration value) {
        if (value == null || value.isZero() || value.isNegative()
                || value.compareTo(Duration.ofMinutes(2)) > 0) {
            throw invalidInput();
        }
        return value;
    }

    private static String requireHeaderValue(String value) {
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw invalidInput();
        }
        return value;
    }

    private static void validateScope(Long bundleId, String indexKey) {
        if (bundleId == null || bundleId < 1 || indexKey == null
                || !indexKey.matches("[0-9a-f]{64}")) {
            throw invalidInput();
        }
    }

    private static List<Double> validVector(List<Double> vector, int dimensions) {
        if (vector == null || vector.size() != dimensions) {
            throw invalidInput();
        }
        for (Double value : vector) {
            if (value == null || !Double.isFinite(value) || !Float.isFinite(value.floatValue())) {
                throw invalidInput();
            }
        }
        return vector;
    }

    private int requiredDimensions() {
        if (dimensions == null) {
            throw unavailable();
        }
        return dimensions;
    }

    private static boolean validChunkId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,150}");
    }

    private static void requireDimensions(int dimensions) {
        if (dimensions < 1 || dimensions > MAX_DIMENSIONS) {
            throw invalidInput();
        }
    }

    private static void requireSuccess(HttpResult result) {
        if (result.status < 200 || result.status >= 300) {
            throw unavailable();
        }
    }

    private static void requireCompleted(HttpResult result) {
        requireSuccess(result);
        if (!"completed".equals(result.body.path("result").path("status").asText())) {
            throw unavailable();
        }
    }

    private static String pointId(Long bundleId, String indexKey, String chunkId) {
        try {
            ObjectNode source = JSON.createObjectNode();
            source.put("bundle_id", bundleId).put("index_key", indexKey).put("chunk_id", chunkId);
            return UUID.nameUUIDFromBytes(JSON.writeValueAsBytes(source)).toString();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static IllegalArgumentException invalidInput() {
        return new IllegalArgumentException("Invalid vector store configuration");
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Knowledge vector store request failed");
    }

    public record Point(String chunkId, List<Double> vector) {
        public Point {
            vector = List.copyOf(vector);
        }
    }

    public record Match(String chunkId, double score) {
    }

    private record HttpResult(int status, JsonNode body) {
    }

    private static class ResponseReadException extends RuntimeException {
        private ResponseReadException(IOException cause) {
            super(cause);
        }
    }
}
