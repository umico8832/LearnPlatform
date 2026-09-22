package com.learnplatform.service.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeSnapshotLoaderTest {
    @Test void loadsTheIsolatedBrowserReviewFixture() {
        var snapshot = new KnowledgeSnapshotLoader().load(repositoryRoot().resolve("frontend/e2e/fixtures/knowledge"));
        assertEquals("e2e-knowledge-review", snapshot.courseKey());
        assertEquals("review_pending", snapshot.qualityStatus());
        assertEquals(2, snapshot.chunks().size());
    }

    @Test
    void loadsTheCommittedKnowledgeSnapshot() {
        KnowledgeSnapshot snapshot = new KnowledgeSnapshotLoader().load(repositoryRoot()
                .resolve("content/knowledge/cs408/v1"));

        assertEquals("cs408-data-structures", snapshot.courseKey());
        assertEquals("v1", snapshot.version());
        assertEquals(snapshot.chunks().size(),
                snapshot.chunks().stream().map(KnowledgeSnapshot.Chunk::id).distinct().count());
    }

    @Test void rejectsTamperedChunkAgainstItsManifestDigest(@TempDir Path temporary) throws Exception {
        Path snapshot = copySnapshot(temporary);
        Path chunks = snapshot.resolve("rag/chunks.jsonl");
        Files.writeString(chunks,
                Files.readString(chunks).replaceFirst("数据、数据元素", "已篡改的数据、数据元素"));

        assertInvalid(snapshot);
    }

    @Test void rejectsAChunkForAnotherCourse(@TempDir Path temporary) throws Exception {
        Path snapshot = copySnapshot(temporary);
        Path chunks = snapshot.resolve("rag/chunks.jsonl");
        Files.writeString(chunks, Files.readString(chunks).replaceFirst("cs408-data-structures", "other-course"));
        updateDigest(snapshot, "rag/chunks.jsonl");

        assertInvalid(snapshot);
    }

    @Test void rejectsDuplicateChunkIdentifiers(@TempDir Path temporary) throws Exception {
        Path snapshot = copySnapshot(temporary);
        Path chunks = snapshot.resolve("rag/chunks.jsonl");
        String first = Files.readAllLines(chunks).getFirst();
        Files.writeString(chunks, Files.readString(chunks) + first + System.lineSeparator());
        updateDigest(snapshot, "rag/chunks.jsonl");

        assertInvalid(snapshot);
    }

    @Test void rejectsSecretLikeManifestEntries(@TempDir Path temporary) throws Exception {
        Path snapshot = copySnapshot(temporary);
        Files.writeString(snapshot.resolve(".env"), "not-a-secret");
        ObjectNode manifest = (ObjectNode) mapper().readTree(snapshot.resolve("manifest.json").toFile());
        ((ObjectNode) manifest.path("files")).put(".env", sha256(Files.readAllBytes(snapshot.resolve(".env"))));
        mapper().writeValue(snapshot.resolve("manifest.json").toFile(), manifest);

        assertInvalid(snapshot);
    }

    @Test void rejectsSecretLikeFilesInsideAllowedDirectories(@TempDir Path temporary) throws Exception {
        Path snapshot = copySnapshot(temporary);
        Path secret = snapshot.resolve("concepts/published/.env");
        Files.writeString(secret, "not-a-secret");
        addManifestFile(snapshot, "concepts/published/.env", secret);

        assertInvalid(snapshot);
    }

    @Test void rejectsUnsupportedExtensionsInsideAllowedDirectories(@TempDir Path temporary) throws Exception {
        Path snapshot = copySnapshot(temporary);
        Path privateKey = snapshot.resolve("sources/private.key");
        Files.writeString(privateKey, "not-a-private-key");
        addManifestFile(snapshot, "sources/private.key", privateKey);

        assertInvalid(snapshot);
    }

    @Test void rejectsDuplicateJsonFields(@TempDir Path temporary) throws Exception {
        Path snapshot = copySnapshot(temporary);
        Path chunks = snapshot.resolve("rag/chunks.jsonl");
        String row = Files.readAllLines(chunks).getFirst();
        Files.writeString(chunks, row.replaceFirst("\\\"chunk_id\\\":",
                "\\\"chunk_id\\\":\\\"duplicate\\\",\\\"chunk_id\\\":"));
        updateDigest(snapshot, "rag/chunks.jsonl");

        assertInvalid(snapshot);
    }

    @Test void rejectsTrailingJsonTokens(@TempDir Path temporary) throws Exception {
        Path snapshot = copySnapshot(temporary);
        Path chunks = snapshot.resolve("rag/chunks.jsonl");
        Files.writeString(chunks, Files.readString(chunks).replaceFirst("\\R", " {}" + System.lineSeparator()));
        updateDigest(snapshot, "rag/chunks.jsonl");

        assertInvalid(snapshot);
    }

    private void assertInvalid(Path snapshot) {
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeSnapshotLoader().load(snapshot));
    }

    private Path copySnapshot(Path temporary) throws IOException {
        Path source = repositoryRoot().resolve("content/knowledge/cs408/v1");
        Path target = temporary.resolve("v1");
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination);
            }
        }
        return target;
    }

    private void updateDigest(Path snapshot, String relative) throws Exception {
        ObjectNode manifest = (ObjectNode) mapper().readTree(snapshot.resolve("manifest.json").toFile());
        ((ObjectNode) manifest.path("files")).put(relative, sha256(Files.readAllBytes(snapshot.resolve(relative))));
        mapper().writeValue(snapshot.resolve("manifest.json").toFile(), manifest);
    }

    private void addManifestFile(Path snapshot, String relative, Path file) throws Exception {
        ObjectNode manifest = (ObjectNode) mapper().readTree(snapshot.resolve("manifest.json").toFile());
        ((ObjectNode) manifest.path("files")).put(relative, sha256(Files.readAllBytes(file)));
        mapper().writeValue(snapshot.resolve("manifest.json").toFile(), manifest);
    }

    private ObjectMapper mapper() {
        return new ObjectMapper();
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !java.nio.file.Files.isDirectory(current.resolve("content"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Repository root is unavailable");
        }
        return current;
    }
}
