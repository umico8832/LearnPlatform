package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.AiCallLogMapper;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.service.AiCallGovernanceService;
import com.learnplatform.service.AiCallReservationService;
import com.learnplatform.service.AiInvocationService;
import com.learnplatform.service.ai.AiCallContext;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.ai.EmbeddingProvider;
import com.learnplatform.service.knowledge.vector.QdrantKnowledgeVectorStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class KnowledgeRetrievalEvaluation {
    static final int TOP_K = 3;
    record Chunk(String id, String text) { }
    record Query(String id, String text, Set<String> relevantIds) { }
    record Corpus(int version, String provenance, List<Chunk> chunks, List<Query> queries) { }
    record Snapshot(Corpus corpus, String hash) { }

    private KnowledgeRetrievalEvaluation() { }

    static Snapshot load() throws IOException {
        try (var input = KnowledgeRetrievalEvaluation.class.getResourceAsStream("/ai-evaluation/retrieval-cases.json")) {
            if (input == null) { throw new IOException("Missing retrieval evaluation corpus"); }
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Corpus corpus = new ObjectMapper().readValue(text, Corpus.class);
            validate(corpus);
            return new Snapshot(corpus, AiEvaluationReport.sha256(text));
        }
    }

    static void validate(Corpus corpus) {
        if (corpus == null || corpus.version() < 1 || blank(corpus.provenance()) || corpus.chunks() == null
                || corpus.queries() == null || corpus.chunks().isEmpty() || corpus.chunks().size() > 32
                || corpus.queries().isEmpty() || corpus.queries().size() > 20) {
            throw new IllegalArgumentException("Invalid retrieval corpus bounds");
        }
        Set<String> ids = new HashSet<>();
        for (Chunk chunk : corpus.chunks()) {
            if (chunk == null || blank(chunk.id()) || blank(chunk.text()) || chunk.text().length() > 6000
                    || !ids.add(chunk.id())) {
                throw new IllegalArgumentException("Invalid retrieval chunk");
            }
        }
        Set<String> queries = new HashSet<>();
        for (Query query : corpus.queries()) {
            if (query == null || blank(query.id()) || blank(query.text()) || query.text().length() > 1000
                    || !queries.add(query.id()) || query.relevantIds() == null || query.relevantIds().isEmpty()
                    || !ids.containsAll(query.relevantIds())) {
                throw new IllegalArgumentException("Invalid retrieval query ground truth");
            }
        }
    }

    static Map<String, Object> run(Snapshot snapshot, AiConfig config, EmbeddingProvider provider,
                                   QdrantKnowledgeVectorStore store, boolean online) {
        validate(snapshot.corpus());
        var logs = new ArrayList<AiCallLog>();
        AiInvocationService invocation = invocation(config, provider, logs);
        var embedding = config.getEmbedding();
        var report = new LinkedHashMap<String, Object>();
        var results = new ArrayList<Map<String, Object>>();
        report.put("schemaVersion", 1);
        report.put("corpusVersion", snapshot.corpus().version());
        report.put("corpusHash", snapshot.hash());
        report.put("provenance", snapshot.corpus().provenance());
        report.put("generatedAt", Instant.now().toString());
        report.put("embeddingOrigin", online ? "REAL_PROVIDER" : "SCRIPTED_FIXTURE");
        report.put("retrievalQuality", online ? "MEASURED_SYNTHETIC_CORPUS" : "NOT_EVALUATED");
        report.put("teachingQuality", "NOT_EVALUATED");
        report.put("scope", "Original micro-corpus, production embedding governance and vector adapter; "
                + "isolated audit mappers; no production knowledge, authorization, or generation evaluation.");
        report.put("model", embedding.getModel());
        report.put("dimensions", embedding.getDimensions());
        var price = config.getModelPrices().get(embedding.getModel());
        report.put("inputPricePerMillion", price == null ? null : price.getInputPerMillion());
        report.put("endpointHash", online ? AiEvaluationReport.sha256(embedding.getApiBaseUrl()) : null);
        report.put("topK", TOP_K);
        report.put("results", results);
        String indexKey = AiEvaluationReport.sha256(snapshot.hash() + embedding.getModel() + embedding.getDimensions());
        UUID runId = UUID.randomUUID();
        try {
            store.ensureCollection(embedding.getDimensions());
            var vectors = embed(invocation, embedding.getModel(), snapshot.corpus().chunks().stream()
                    .map(Chunk::text).toList(), "rag_eval_index", runId);
            var points = new ArrayList<QdrantKnowledgeVectorStore.Point>();
            for (int i = 0; i < vectors.size(); i++) {
                points.add(new QdrantKnowledgeVectorStore.Point(snapshot.corpus().chunks().get(i).id(), vectors.get(i)));
            }
            store.upsert(1L, indexKey, points);
            for (Query query : snapshot.corpus().queries()) {
                var vector = embed(invocation, embedding.getModel(), List.of(query.text()), "rag_eval_query", runId).getFirst();
                long start = System.nanoTime();
                var matches = store.search(1L, indexKey, vector, TOP_K);
                long retrievalMs = (System.nanoTime() - start) / 1_000_000;
                var ids = matches.stream().map(QdrantKnowledgeVectorStore.Match::chunkId).toList();
                if (!snapshot.corpus().chunks().stream().map(Chunk::id).toList().containsAll(ids)) {
                    throw new IllegalStateException("Retrieval returned unknown chunk");
                }
                var row = new LinkedHashMap<String, Object>();
                row.put("queryId", query.id());
                row.put("query", query.text());
                row.put("relevantIds", query.relevantIds());
                row.put("matches", matches);
                row.put("metrics", KnowledgeRetrievalMetrics.evaluate(query.relevantIds(), ids, TOP_K));
                row.put("retrievalMs", retrievalMs);
                results.add(row);
            }
            var metrics = results.stream().map(row -> (KnowledgeRetrievalMetrics.Result) row.get("metrics")).toList();
            report.put("meanRecallAtK", metrics.stream().mapToDouble(KnowledgeRetrievalMetrics.Result::recallAtK)
                    .average().orElseThrow());
            report.put("meanReciprocalRankAtK", metrics.stream()
                    .mapToDouble(KnowledgeRetrievalMetrics.Result::reciprocalRankAtK).average().orElseThrow());
            report.put("hitRateAtK", (double) metrics.stream().filter(KnowledgeRetrievalMetrics.Result::hitAtK).count()
                    / metrics.size());
            report.put("executionStatus", "SUCCEEDED");
        } catch (RuntimeException error) {
            report.put("executionStatus", "FAILED");
            report.put("errorType", error instanceof ModelException model ? model.code().name() : error.getClass().getSimpleName());
            report.put("retrievalQuality", "INCOMPLETE");
        }
        report.put("modelCalls", logs.size());
        report.put("callMetrics", logs.stream().map(AiEvaluationReport::callMetric).toList());
        report.put("usage", AiEvaluationReport.totalUsage(logs));
        report.put("costUsd", AiEvaluationReport.totalCost(logs));
        return report;
    }

    private static List<List<Double>> embed(AiInvocationService invocation, String model, List<String> inputs,
                                            String function, UUID runId) {
        var result = invocation.embed(new AiCallContext(7L, function, runId),
                invocation.embeddingRequest(inputs), new Cancellation());
        if (result.vectors().size() != inputs.size() || result.model() != null && !model.equals(result.model())) {
            throw new ModelException(ModelException.Code.PROTOCOL, result.auditResult());
        }
        return result.vectors();
    }

    private static AiInvocationService invocation(AiConfig config, EmbeddingProvider provider, List<AiCallLog> logs) {
        var mapper = mock(AiCallLogMapper.class);
        var users = mock(UserMapper.class);
        when(users.lockAiQuotaUser(7L)).thenReturn(new User());
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.updateById(any())).thenReturn(1);
        doAnswer(call -> { logs.add(call.getArgument(0)); return 1; }).when(mapper).insert(any());
        var governance = new AiCallGovernanceService(config, mapper, users,
                new AiCallReservationService(users, mapper, config), new ObjectMapper());
        return new AiInvocationService(mock(AiProvider.class), provider, governance, config);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
