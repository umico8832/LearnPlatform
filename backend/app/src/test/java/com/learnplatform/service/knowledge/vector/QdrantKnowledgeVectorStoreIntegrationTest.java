package com.learnplatform.service.knowledge.vector;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
@Testcontainers
class QdrantKnowledgeVectorStoreIntegrationTest {
    private static final String KEY_A = "a".repeat(64);
    private static final String KEY_B = "b".repeat(64);

    @Container
    static final GenericContainer<?> QDRANT = new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.16.0"))
            .withExposedPorts(6333);

    @Test
    void isolatesBundleAndIndexAndMakesStablePointUpsertsIdempotent() {
        try (QdrantKnowledgeVectorStore store = store()) {
            store.ensureCollection(3);
            store.upsert(7L, KEY_A, List.of(point("chunk-a", 1D, 0D, 0D)));
            store.upsert(7L, KEY_A, List.of(point("chunk-a", 1D, 0D, 0D)));
            store.upsert(7L, KEY_B, List.of(point("chunk-b", 1D, 0D, 0D)));
            store.upsert(8L, KEY_A, List.of(point("chunk-c", 1D, 0D, 0D)));

            assertEquals(List.of(new QdrantKnowledgeVectorStore.Match("chunk-a", 1D)),
                    store.search(7L, KEY_A, List.of(1D, 0D, 0D), 10));
            assertEquals(List.of(new QdrantKnowledgeVectorStore.Match("chunk-b", 1D)),
                    store.search(7L, KEY_B, List.of(1D, 0D, 0D), 10));
            store.delete(7L, KEY_A);
            assertEquals(List.of(), store.search(7L, KEY_A, List.of(1D, 0D, 0D), 10));
        }
    }

    @Test
    void rejectsVectorsOutsideTheVerifiedCollectionDimension() {
        try (QdrantKnowledgeVectorStore store = store()) {
            store.ensureCollection(3);
            assertThrows(IllegalArgumentException.class,
                    () -> store.upsert(9L, KEY_A, List.of(point("chunk-d", 1D, 0D))));
        }
    }

    private QdrantKnowledgeVectorStore store() {
        return new QdrantKnowledgeVectorStore("http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333),
                null, "knowledge-integration", Duration.ofSeconds(10));
    }

    private QdrantKnowledgeVectorStore.Point point(String chunkId, Double... values) {
        return new QdrantKnowledgeVectorStore.Point(chunkId, List.of(values));
    }
}
