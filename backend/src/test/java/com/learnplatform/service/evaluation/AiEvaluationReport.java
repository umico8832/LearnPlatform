package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.config.AiConfig;
import com.learnplatform.service.ai.AiCostCalculator;

import java.io.IOException;
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
        result.put("promptHash", system == null ? null : sha256("system:" + system + "\nuser:" + user));
        result.put("systemPrompt", system);
        result.put("userPrompt", user);
        result.put("modelConfigVersion", fixture.logs.isEmpty() ? null : fixture.logs.get(0).getModelConfigVersion());
        result.put("response", fixture.provider.response);
        result.put("usage", fixture.provider.calls == 0 ? null : fixture.provider.getLastTokenUsage());
        result.put("costUsd", fixture.provider.calls == 0 ? null : new AiCostCalculator(config)
                .calculate(config.getModel(), fixture.provider.getLastTokenUsage()));
        result.put("teachingQuality", online ? "NOT_REVIEWED" : "NOT_EVALUATED");
        result.put("manualCriteria", sample.manualCriteria());
        return result;
    }

    static void write(AiEvaluationCorpus corpus, AiConfig config, boolean online,
                      List<Map<String, Object>> results) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
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
        report.put("temperature", 0.7);
        report.put("timeoutMs", config.getTimeout());
        report.put("streamIncludeUsage", config.isStreamIncludeUsage());
        report.put("scope", "Real business services and governance; synthetic mapper/session fixtures; no database or HTTP authorization integration.");
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
