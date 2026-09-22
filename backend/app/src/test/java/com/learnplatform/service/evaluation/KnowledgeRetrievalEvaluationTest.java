package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.ai.model.EmbeddingResult;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.config.AiConfig;
import com.learnplatform.service.ai.EmbeddingProvider;
import com.learnplatform.service.knowledge.vector.QdrantKnowledgeVectorStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KnowledgeRetrievalEvaluationTest {
    @Test void requiresExplicitIndependentEmbeddingConfiguration() {
        assertThrows(AssertionError.class, () -> KnowledgeRetrievalEvaluationOnlineTest.configuration(Map.of()));
        var missingModel = environment();
        missingModel.remove("AI_EMBEDDING_MODEL");
        assertThrows(AssertionError.class, () -> KnowledgeRetrievalEvaluationOnlineTest.configuration(missingModel));
        var invalidDimensions = environment();
        invalidDimensions.put("AI_EMBEDDING_DIMENSIONS", "0");
        assertThrows(AssertionError.class, () -> KnowledgeRetrievalEvaluationOnlineTest.configuration(invalidDimensions));
        for (String endpoint : List.of("", "file:///tmp/vector", "https://user:fixture@example.invalid/v1",
                "https://example.invalid/v1?key=fixture")) {
            var unsafe = environment();
            unsafe.put("AI_EMBEDDING_API_BASE_URL", endpoint);
            assertThrows(AssertionError.class, () -> KnowledgeRetrievalEvaluationOnlineTest.configuration(unsafe));
        }
        var config = KnowledgeRetrievalEvaluationOnlineTest.configuration(environment());
        assertFalse(config.isEnabled());
        assertTrue(config.getEmbedding().isEnabled());
        assertEquals(2, config.getEmbedding().getDimensions());
    }

    @Test void rejectsGroundTruthOutsideTheCorpusBeforeProviderCalls() throws Exception {
        var loaded = KnowledgeRetrievalEvaluation.load();
        assertEquals(64, loaded.hash().length());
        var corpus = loaded.corpus();
        var invalid = new KnowledgeRetrievalEvaluation.Corpus(corpus.version(), corpus.provenance(), corpus.chunks(),
                List.of(new KnowledgeRetrievalEvaluation.Query("invalid", "question", Set.of("missing-id"))));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeRetrievalEvaluation.validate(invalid));
    }

    @Test void usesOnlyAnExplicitInputPriceAndRejectsInvalidPrices() {
        var environment = environment();
        environment.put("AI_RAG_EVAL_INPUT_PRICE_PER_MILLION", "0.25");
        var config = KnowledgeRetrievalEvaluationOnlineTest.configuration(environment);
        assertEquals(new BigDecimal("0.25"), config.getModelPrices().get("fixture-embedding").getInputPerMillion());
        environment.put("AI_RAG_EVAL_INPUT_PRICE_PER_MILLION", "-1");
        assertThrows(AssertionError.class, () -> KnowledgeRetrievalEvaluationOnlineTest.configuration(environment));
        environment.put("AI_RAG_EVAL_INPUT_PRICE_PER_MILLION", "invalid");
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeRetrievalEvaluationOnlineTest.configuration(environment));
    }

    @Test void reportsRankedResultsAndActualAuditedUsageWithoutPromotingFixturesToQualityEvidence() throws Exception {
        var snapshot = KnowledgeRetrievalEvaluation.load();
        var config = KnowledgeRetrievalEvaluationOnlineTest.configuration(environment());
        var price = new AiConfig.ModelPrice();
        price.setInputPerMillion(new BigDecimal("2"));
        config.setModelPrices(Map.of("fixture-embedding", price));
        var provider = mock(EmbeddingProvider.class);
        when(provider.embed(any(), any())).thenAnswer(call -> {
            EmbeddingRequest request = call.getArgument(0);
            return new EmbeddingResult(request.inputs().stream().map(input -> List.of(1D, 0D)).toList(),
                    "fixture-embedding", new ModelResult.Usage(10, 0, 10));
        });
        var store = mock(QdrantKnowledgeVectorStore.class);
        when(store.search(eq(1L), anyString(), anyList(), eq(3))).thenReturn(List.of(
                new QdrantKnowledgeVectorStore.Match(snapshot.corpus().chunks().getFirst().id(), 0.9)));
        var report = KnowledgeRetrievalEvaluation.run(snapshot, config, provider, store, false);
        int calls = 1 + snapshot.corpus().queries().size();
        assertEquals("SUCCEEDED", report.get("executionStatus"));
        assertEquals(calls, report.get("modelCalls"));
        assertEquals("SCRIPTED_FIXTURE", report.get("embeddingOrigin"));
        assertEquals("NOT_EVALUATED", report.get("retrievalQuality"));
        assertEquals("NOT_EVALUATED", report.get("teachingQuality"));
        assertEquals(0, new BigDecimal("0.000020").multiply(BigDecimal.valueOf(calls))
                .compareTo((BigDecimal) report.get("costUsd")));
        var json = new ObjectMapper().valueToTree(report);
        assertEquals(calls * 10, json.path("usage").path("totalTokens").asInt());
        assertEquals(snapshot.corpus().queries().size(), json.path("results").size());
        assertEquals("rag_eval_index", json.path("callMetrics").get(0).path("function").asText());
        assertFalse(json.toString().contains("fixture-only-key"));
        verify(store).upsert(eq(1L), anyString(), argThat(points -> points.size() == snapshot.corpus().chunks().size()));
        verify(store, times(snapshot.corpus().queries().size())).search(eq(1L), anyString(), anyList(), eq(3));
    }

    @Test void failedEmbeddingRetainsSanitizedAuditAndCannotClaimCompletedMetrics() throws Exception {
        var provider = mock(EmbeddingProvider.class);
        when(provider.embed(any(), any())).thenThrow(new ModelException(ModelException.Code.UPSTREAM));
        var store = mock(QdrantKnowledgeVectorStore.class);
        var report = KnowledgeRetrievalEvaluation.run(KnowledgeRetrievalEvaluation.load(),
                KnowledgeRetrievalEvaluationOnlineTest.configuration(environment()), provider, store, false);
        assertEquals("FAILED", report.get("executionStatus"));
        assertEquals("UPSTREAM", report.get("errorType"));
        assertEquals("INCOMPLETE", report.get("retrievalQuality"));
        assertNull(report.get("usage"));
        assertNull(report.get("costUsd"));
        assertFalse(report.containsKey("meanRecallAtK"));
        assertEquals(1, report.get("modelCalls"));
        verify(store, never()).search(any(), anyString(), anyList(), anyInt());
        verify(store, never()).upsert(any(), anyString(), anyList());
    }

    private Map<String, Object> environment() {
        return new LinkedHashMap<>(Map.of("AI_EMBEDDING_ENABLED", "true", "AI_EMBEDDING_API_KEY", "fixture-only-key",
                "AI_EMBEDDING_MODEL", "fixture-embedding", "AI_EMBEDDING_DIMENSIONS", "2",
                "AI_EMBEDDING_API_BASE_URL", "https://embedding.invalid/v1"));
    }
}
