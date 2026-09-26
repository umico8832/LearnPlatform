package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.service.ai.AiTokenUsage;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AiEvaluationReport {
    private AiEvaluationReport() { }

    static Map<String, Object> result(AiEvaluationCorpus.Case sample, AiEvaluationFixture fixture,
                                      AiConfig config, boolean online, List<String> failures) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseId", sample.id());
        result.put("category", sample.category());
        result.put("responseOrigin", online ? "REAL_PROVIDER" : "SCRIPTED_FIXTURE");
        result.put("contractStatus", failures.isEmpty() ? "PASS" : "FAIL");
        result.put("failures", failures);
        result.put("expectedCode", sample.expectedCode());
        result.put("actualCode", fixture.actualCode);
        result.put("errorType", fixture.errorType);
        result.put("providerCalls", fixture.provider.calls);
        String system = fixture.provider.systemPrompt;
        String user = fixture.provider.userPrompt;
        result.put("promptHash", fixture.logs.isEmpty() ? null : fixture.logs.get(0).getPromptHash());
        result.put("systemPrompt", system);
        result.put("userPrompt", user);
        result.put("modelConfigVersion", fixture.logs.isEmpty() ? null : fixture.logs.get(0).getModelConfigVersion());
        result.put("response", fixture.provider.response);
        result.put("publicOutput", fixture.publicOutput);
        result.put("toolTrace", fixture.toolTrace);
        result.put("retrievalOrigin", sample.usesRetrieval() ? "SYNTHETIC_TOOL_FIXTURE" : "NOT_USED");
        result.put("retrievalQuality", "NOT_EVALUATED");
        result.put("usage", totalUsage(fixture.logs));
        result.put("costUsd", totalCost(fixture.logs));
        result.put("modelCalls", fixture.logs.size());
        result.put("callMetrics", fixture.logs.stream().map(AiEvaluationReport::callMetric).toList());
        result.put("teachingQuality", online ? "NOT_REVIEWED" : "NOT_EVALUATED");
        result.put("manualCriteria", sample.manualCriteria());
        return result;
    }

    static Map<String, Object> callMetric(AiCallLog log) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("outcome", log.getOutcome());
        metric.put("function", log.getFunctionType());
        metric.put("model", log.getModel());
        metric.put("finishReason", log.getFinishReason());
        metric.put("runId", log.getRunId());
        metric.put("promptHash", log.getPromptHash());
        metric.put("durationMs", log.getDuration());
        metric.put("inputTokens", log.getPromptTokens());
        metric.put("outputTokens", log.getCompletionTokens());
        metric.put("totalTokens", log.getTokensUsed());
        metric.put("costUsd", log.getCostUsd());
        return metric;
    }

    static AiTokenUsage totalUsage(List<AiCallLog> logs) {
        if (logs.isEmpty() || logs.stream().anyMatch(log -> log.getPromptTokens() == null
                || log.getCompletionTokens() == null || log.getTokensUsed() == null)) {
            return null;
        }
        return new AiTokenUsage(logs.stream().mapToInt(AiCallLog::getPromptTokens).sum(),
                logs.stream().mapToInt(AiCallLog::getCompletionTokens).sum(),
                logs.stream().mapToInt(AiCallLog::getTokensUsed).sum());
    }

    static BigDecimal totalCost(List<AiCallLog> logs) {
        if (logs.isEmpty() || logs.stream().anyMatch(log -> log.getCostUsd() == null)) {
            return null;
        }
        return logs.stream().map(AiCallLog::getCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static void write(AiEvaluationCorpus corpus, AiConfig config, boolean online,
                      List<Map<String, Object>> results) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 4);
        report.put("corpusVersion", corpus.version());
        try (var input = AiEvaluationCorpus.class.getResourceAsStream("/ai-evaluation/cases.json")) {
            if (input == null) { throw new IOException("Missing evaluation corpus"); }
            report.put("corpusHash", sha256(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        report.put("generatedAt", Instant.now().toString());
        report.put("mode", online ? "ONLINE" : "OFFLINE");
        report.put("model", config.getModel());
        report.put("endpointHash", online ? sha256(config.getApiBaseUrl()) : null);
        report.put("maxTokens", config.getMaxTokens());
        report.put("temperature", config.getTemperature());
        report.put("timeoutMs", config.getTimeout());
        report.put("streamIncludeUsage", config.isStreamIncludeUsage());
        report.put("toolsSupported", config.isToolsSupported());
        report.put("scope", "Real business services, Agent runtime, and governance; "
                + "synthetic mapper/session/tool fixtures; no database or HTTP authorization integration; "
                + "no real embedding, vector retrieval, or retrieval quality measurement.");
        report.put("teachingQuality", online ? "NOT_REVIEWED" : "NOT_EVALUATED");
        report.put("results", results);
        Path output = Path.of("target", "ai-evaluation", online ? "online.json" : "offline.json");
        Files.createDirectories(output.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
