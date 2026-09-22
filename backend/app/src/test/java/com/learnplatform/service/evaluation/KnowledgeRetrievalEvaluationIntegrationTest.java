package com.learnplatform.service.evaluation;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.ai.model.EmbeddingResult;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.config.AiConfig;
import com.learnplatform.service.ai.EmbeddingProvider;
import com.learnplatform.service.knowledge.vector.QdrantKnowledgeVectorStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Testcontainers
class KnowledgeRetrievalEvaluationIntegrationTest {
    private static final int DIMENSIONS = 8;

    @Container
    static final GenericContainer<?> QDRANT = new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.16.0"))
            .withExposedPorts(6333);

    @Test void runsTheScriptedCorpusThroughGovernedEmbeddingAndQdrant() throws Exception {
        var snapshot = KnowledgeRetrievalEvaluation.load();
        try (var store = new QdrantKnowledgeVectorStore(
                "http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333), null,
                "retrieval_evaluation_" + UUID.randomUUID().toString().replace('-', '_'), Duration.ofSeconds(30))) {
            Map<String, Object> report = KnowledgeRetrievalEvaluation.run(snapshot, configuration(),
                    new ScriptedOneHotEmbeddingProvider(snapshot), store, false);

            assertEquals("SUCCEEDED", report.get("executionStatus"));
            assertEquals(snapshot.corpus().queries().size() + 1, report.get("modelCalls"));
            assertEquals("NOT_EVALUATED", report.get("retrievalQuality"));
            assertEquals("NOT_EVALUATED", report.get("teachingQuality"));
            assertRankedHitsAreRelevant(report, snapshot);
        }
    }

    private AiConfig configuration() {
        var config = new AiConfig();
        var embedding = new AiConfig.EmbeddingConfig();
        embedding.setEnabled(true);
        embedding.setApiBaseUrl("http://scripted.invalid/v1");
        embedding.setModel("scripted-onehot");
        embedding.setDimensions(DIMENSIONS);
        embedding.setTimeoutSeconds(30);
        config.setEmbedding(embedding);
        return config;
    }

    @SuppressWarnings("unchecked")
    private void assertRankedHitsAreRelevant(Map<String, Object> report, KnowledgeRetrievalEvaluation.Snapshot snapshot) {
        Map<String, KnowledgeRetrievalEvaluation.Query> queries = new HashMap<>();
        snapshot.corpus().queries().forEach(query -> queries.put(query.id(), query));
        for (Map<String, Object> row : (List<Map<String, Object>>) report.get("results")) {
            var matches = (List<QdrantKnowledgeVectorStore.Match>) row.get("matches");
            assertTrue(!matches.isEmpty());
            assertTrue(queries.get(row.get("queryId")).relevantIds().contains(matches.getFirst().chunkId()));
        }
    }

    private static final class ScriptedOneHotEmbeddingProvider implements EmbeddingProvider {
        private final Map<String, Integer> vectorByInput;

        private ScriptedOneHotEmbeddingProvider(KnowledgeRetrievalEvaluation.Snapshot snapshot) {
            vectorByInput = new HashMap<>();
            for (int index = 0; index < snapshot.corpus().chunks().size(); index++) {
                vectorByInput.put(snapshot.corpus().chunks().get(index).text(), index);
            }
            for (KnowledgeRetrievalEvaluation.Query query : snapshot.corpus().queries()) {
                String relevantId = query.relevantIds().iterator().next();
                int index = snapshot.corpus().chunks().stream().map(KnowledgeRetrievalEvaluation.Chunk::id)
                        .toList().indexOf(relevantId);
                vectorByInput.put(query.text(), index);
            }
        }

        @Override
        public void validate(EmbeddingRequest request) {
            if (!"scripted-onehot".equals(request.model()) || !Integer.valueOf(DIMENSIONS).equals(request.dimensions())) {
                throw new IllegalArgumentException("Unexpected scripted embedding request");
            }
        }

        @Override
        public EmbeddingResult embed(EmbeddingRequest request, Cancellation cancellation) {
            cancellation.check();
            return new EmbeddingResult(request.inputs().stream().map(this::vector).toList(), request.model(),
                    new ModelResult.Usage(request.inputs().size(), 0, request.inputs().size()));
        }

        private List<Double> vector(String input) {
            Integer index = vectorByInput.get(input);
            if (index == null) {
                throw new IllegalArgumentException("Unknown scripted embedding input");
            }
            return java.util.stream.IntStream.range(0, DIMENSIONS)
                    .mapToObj(position -> position == index ? 1D : 0D).toList();
        }
    }
}
