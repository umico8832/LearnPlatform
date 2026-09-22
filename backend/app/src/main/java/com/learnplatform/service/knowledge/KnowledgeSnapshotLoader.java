package com.learnplatform.service.knowledge;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class KnowledgeSnapshotLoader {
    private static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 32L * 1024 * 1024;
    private static final int MAX_FILES = 256;
    private static final Set<String> QUALITY_STATUSES = Set.of("review_pending", "reviewed");
    private final ObjectMapper json;

    public KnowledgeSnapshotLoader() {
        this(new ObjectMapper());
    }

    public KnowledgeSnapshotLoader(ObjectMapper json) {
        this.json = json.copy()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public KnowledgeSnapshot load(Path root) {
        try {
            Path snapshotRoot = root.toRealPath();
            if (!Files.isDirectory(snapshotRoot)) {
                throw invalid();
            }
            byte[] manifestBytes = readManifestBytes(snapshotRoot);
            JsonNode manifest = object(json.readTree(decodeUtf8(manifestBytes)));
            require(manifest.path("schema_version").isInt() && manifest.path("schema_version").intValue() == 1);
            String course = boundedText(manifest, "course_id", 100);
            String version = boundedText(manifest, "bundle_version", 100);
            String sourceRevision = boundedText(manifest, "source_revision", 100);
            String qualityStatus = quality(manifest.path("quality_status"));
            JsonNode files = object(manifest.path("files"));
            require(files.size() > 0 && files.size() <= MAX_FILES);

            Map<String, String> listed = new HashMap<>();
            Map<String, byte[]> content = new HashMap<>();
            long totalBytes = 0;
            var fields = files.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String relative = entry.getKey();
                String expectedHash = text(entry.getValue(), 64);
                require(isAllowedContentPath(relative) && expectedHash.matches("[0-9a-f]{64}"));
                Path file = safeFile(snapshotRoot, relative);
                byte[] bytes = readBounded(file);
                totalBytes += bytes.length;
                require(totalBytes <= MAX_TOTAL_BYTES && expectedHash.equals(sha256(bytes)));
                listed.put(relative, expectedHash);
                content.put(relative, bytes);
            }
            require(actualFiles(snapshotRoot).equals(listed.keySet()));

            JsonNode taxonomy = object(readJson(content, "taxonomy.json"));
            JsonNode coverage = object(readJson(content, "coverage.json"));
            require(course.equals(boundedText(taxonomy, "course_id", 100))
                    && course.equals(boundedText(coverage, "course_id", 100))
                    && qualityStatus.equals(quality(coverage.path("review_status"))));

            Map<String, String> conceptQuality = loadConceptQualities(listed.keySet(), content, course, qualityStatus);
            List<KnowledgeSnapshot.Chunk> chunks = loadChunks(content, conceptQuality, course);
            verifyCounts(manifest.path("counts"), conceptQuality.size(), chunks.size(), coverage, content);
            return new KnowledgeSnapshot(course, version, sha256(manifestBytes),
                    sourceRevision, qualityStatus, chunks);
        } catch (IOException | RuntimeException ignored) {
            throw invalid();
        }
    }

    private Map<String, String> loadConceptQualities(Set<String> listed,
                                                      Map<String, byte[]> content,
                                                      String course,
                                                      String snapshotStatus) throws IOException {
        Map<String, String> concepts = new HashMap<>();
        for (String relative : listed) {
            if (!relative.startsWith("concepts/published/") || !relative.endsWith(".jsonl")) {
                continue;
            }
            for (JsonNode concept : jsonLines(content.get(relative))) {
                String id = boundedText(concept, "id", 150);
                String conceptStatus = quality(concept.path("quality").path("status"));
                require(snapshotStatus.equals(conceptStatus) && concepts.putIfAbsent(id, conceptStatus) == null);
                require(course.equals(boundedText(object(concept.path("location")), "course_id", 100)));
            }
        }
        require(!concepts.isEmpty());
        return concepts;
    }

    private List<KnowledgeSnapshot.Chunk> loadChunks(Map<String, byte[]> content,
                                                      Map<String, String> concepts,
                                                      String course) throws IOException {
        List<JsonNode> rows = jsonLines(content.get("rag/chunks.jsonl"));
        List<KnowledgeSnapshot.Chunk> chunks = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode row : rows) {
            String id = boundedText(row, "chunk_id", 150);
            String conceptId = boundedText(row, "concept_id", 150);
            String conceptStatus = concepts.get(conceptId);
            require(ids.add(id) && conceptStatus != null);
            String title = boundedText(row, "title", 255);
            String body = boundedText(row, "text", MAX_FILE_BYTES);
            JsonNode metadata = object(row.path("metadata"));
            require(course.equals(boundedText(metadata, "course_id", 100)));
            chunks.add(new KnowledgeSnapshot.Chunk(id, conceptId, title, body,
                    sha256(body.getBytes(StandardCharsets.UTF_8)), json.writeValueAsString(metadata), conceptStatus));
        }
        return chunks;
    }

    private void verifyCounts(JsonNode counts, int concepts, int chunks,
                              JsonNode coverage, Map<String, byte[]> content) throws IOException {
        JsonNode value = object(counts);
        require(value.path("concepts").isInt() && value.path("concepts").intValue() == concepts);
        require(value.path("chunks").isInt() && value.path("chunks").intValue() == chunks);
        JsonNode items = coverage.path("items");
        require(items.isArray() && value.path("coverage_items").isInt()
                && value.path("coverage_items").intValue() == items.size());
        require(value.path("relations").isInt()
                && value.path("relations").intValue() == jsonLines(content.get("relations/relations.jsonl")).size());
    }

    private JsonNode readJson(Map<String, byte[]> content, String relative) throws IOException {
        byte[] bytes = content.get(relative);
        require(bytes != null);
        return json.readTree(decodeUtf8(bytes));
    }

    private byte[] readManifestBytes(Path root) throws IOException {
        Path manifest = root.resolve("manifest.json");
        require(Files.isRegularFile(manifest) && !Files.isSymbolicLink(manifest));
        return readBounded(manifest);
    }

    private List<JsonNode> jsonLines(byte[] bytes) throws IOException {
        require(bytes != null);
        require(bytes.length <= MAX_FILE_BYTES);
        String[] lines = decodeUtf8(bytes).split("\\R", -1);
        List<JsonNode> values = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].isBlank()) {
                require(index == lines.length - 1);
                continue;
            }
            values.add(object(json.readTree(lines[index])));
        }
        return values;
    }

    private Set<String> actualFiles(Path root) throws IOException {
        Set<String> result = new HashSet<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                if (path.equals(root)) {
                    continue;
                }
                require(!Files.isSymbolicLink(path));
                if (Files.isRegularFile(path)) {
                    result.add(root.relativize(path).toString().replace('\\', '/'));
                }
            }
        }
        result.remove("manifest.json");
        require(result.size() <= MAX_FILES);
        return result;
    }

    private Path safeFile(Path root, String relative) throws IOException {
        require(isAllowedContentPath(relative));
        Path target = root.resolve(relative).normalize();
        require(target.startsWith(root) && Files.isRegularFile(target) && !Files.isSymbolicLink(target));
        Path current = root;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            require(!Files.isSymbolicLink(current));
        }
        require(target.toRealPath().startsWith(root));
        return target;
    }

    private static boolean isAllowedContentPath(String value) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.contains("\\") || value.contains("..")
                || value.equals("manifest.json")) {
            return false;
        }
        if (value.equals("taxonomy.json") || value.equals("coverage.json")) {
            return true;
        }
        return value.matches("(?:concepts/(?:internal|published)|rag|relations|source_manifests|sources)"
                + "/[^/.][^/]*\\.(?:json|jsonl)");
    }

    private static byte[] readBounded(Path file) throws IOException {
        try (InputStream source = Files.newInputStream(file)) {
            byte[] bytes = source.readNBytes((int) MAX_FILE_BYTES + 1);
            require(bytes.length <= MAX_FILE_BYTES);
            return bytes;
        }
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }

    private static JsonNode object(JsonNode value) {
        require(value != null && value.isObject());
        return value;
    }

    private static String boundedText(JsonNode object, String name, long limit) {
        return text(object.path(name), limit);
    }

    private static String text(JsonNode value, long limit) {
        require(value != null && value.isTextual() && !value.textValue().isBlank()
                && value.textValue().length() <= limit);
        return value.textValue();
    }

    private static String quality(JsonNode value) {
        String status = text(value, 32);
        require(QUALITY_STATUSES.contains(status));
        return status;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid knowledge snapshot");
    }
}
