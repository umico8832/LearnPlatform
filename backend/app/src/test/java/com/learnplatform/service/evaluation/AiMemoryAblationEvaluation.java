package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.config.AiConfig;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.ai.AiTokenUsage;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class AiMemoryAblationEvaluation {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCRIPTED_RESPONSE = "栈遵循后进先出。先观察每次操作前后的栈顶，再继续本节学习。";
    private static final String LESSON = "栈只在栈顶插入或删除，遵循后进先出。"
            + "空栈先压入 1，再压入 2，栈顶为 2；弹出一次后，栈顶恢复为 1。"
            + "栈中元素数量不等于栈顶元素的值。空栈不能继续弹出。";

    private AiMemoryAblationEvaluation() { }

    record Settings(Set<String> selected, int repetitions) {
        Settings {
            selected = Set.copyOf(selected);
            if (repetitions < 1 || repetitions > 4) {
                throw new IllegalArgumentException("AI_MEMORY_EVAL_REPETITIONS must be between 1 and 4");
            }
        }

        static Settings from(Map<String, String> environment, AiMemoryAblationCorpus corpus) {
            Set<String> selected = new LinkedHashSet<>();
            for (String id : environment.getOrDefault("AI_MEMORY_EVAL_CASES", "").split(",")) {
                if (!id.isBlank()) { selected.add(id.trim()); }
            }
            Set<String> eligible = new LinkedHashSet<>();
            corpus.cases().forEach(sample -> eligible.add(sample.id()));
            if (!eligible.containsAll(selected)) {
                throw new IllegalArgumentException("AI_MEMORY_EVAL_CASES must contain memory corpus IDs");
            }
            int repetitions;
            try {
                repetitions = Integer.parseInt(environment.getOrDefault("AI_MEMORY_EVAL_REPETITIONS", "1"));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("AI_MEMORY_EVAL_REPETITIONS must be between 1 and 4");
            }
            return new Settings(selected, repetitions);
        }
    }

    static Map<String, Object> run(AiMemoryAblationCorpus corpus, AiConfig config,
                                   AiProvider provider, Settings settings) throws IOException {
        if (settings.selected().stream().anyMatch(id -> corpus.cases().stream()
                .noneMatch(sample -> sample.id().equals(id)))) {
            throw new IllegalArgumentException("Unknown memory corpus IDs");
        }
        boolean online = provider != null;
        List<Map<String, Object>> pairs = new ArrayList<>();
        for (int index = 0; index < corpus.cases().size(); index++) {
            var sample = corpus.cases().get(index);
            if (!settings.selected().isEmpty() && !settings.selected().contains(sample.id())) { continue; }
            for (int repetition = 1; repetition <= settings.repetitions(); repetition++) {
                pairs.add(pair(sample, config, provider, repetition, index));
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("experimentId", UUID.randomUUID().toString());
        report.put("generatedAt", Instant.now().toString());
        report.put("corpusVersion", corpus.version());
        report.put("provenance", corpus.provenance());
        try (var input = AiMemoryAblationCorpus.class.getResourceAsStream(AiMemoryAblationCorpus.RESOURCE)) {
            if (input == null) { throw new IOException("Missing memory ablation corpus"); }
            report.put("corpusHash", AiEvaluationReport.sha256(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        report.put("fixtureImplementationHash", implementationHash());
        report.put("mode", online ? "ONLINE" : "OFFLINE");
        report.put("model", config.getModel());
        report.put("endpointHash", online ? AiEvaluationReport.sha256(config.getApiBaseUrl()) : null);
        report.put("maxTokens", config.getMaxTokens());
        report.put("temperature", config.getTemperature());
        report.put("timeoutMs", config.getTimeout());
        report.put("toolsSupported", config.isToolsSupported());
        report.put("repetitions", settings.repetitions());
        report.put("plannedTrials", pairs.size() * 4);
        report.put("maxProviderCalls", pairs.size() * 16);
        report.put("orderPolicy", "Rotate conditions by corpus index plus repetition; four repeats balance positions.");
        report.put("history", List.of());
        report.put("lesson", LESSON);
        report.put("comparisonStatus", online ? "NOT_REVIEWED" : "NOT_EVALUATED");
        report.put("scope", "Production Agent runtime/governance with synthetic memory and tools; "
                + "only memory data changes within a pair. Empty memory retains the production policy/envelope. "
                + "No database, authorization, persistence, RAG, or real learner outcome evaluation. "
                + "Subsequent model-selected tool paths may differ. No automatic teaching quality score.");
        report.put("contractStatus", pairs.stream().allMatch(pair -> "PASS".equals(pair.get("contractStatus")))
                ? "PASS" : "FAIL");
        report.put("pairs", pairs);
        return report;
    }

    private static Map<String, Object> pair(AiMemoryAblationCorpus.Case sample, AiConfig config,
                                             AiProvider provider, int repetition, int index) throws IOException {
        List<AiMemoryAblationCorpus.Condition> order = order(index, repetition);
        List<Map<String, Object>> trials = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        ModelRequest invariant = null;
        for (var condition : order) {
            String id = sample.id() + "/" + repetition + "/" + condition;
            var scenario = new AiEvaluationCorpus.Case(id, "AGENT", "CHAT", "memory", "knowledge", "NORMAL",
                    true, SCRIPTED_RESPONSE, 0, List.of(), List.of(), List.of("EVAL_INJECTION_CANARY"),
                    sample.manualCriteria(), List.of());
            var question = new AiEvaluationCorpus.QuestionData(sample.question(), List.of(), "", LESSON,
                    "栈", "408 数据结构", null);
            var fixtureCorpus = new AiEvaluationCorpus(1, "Synthetic memory ablation fixtures",
                    Map.of("memory", question), List.of(scenario));
            String context = condition.context(sample);
            var fixture = new AiEvaluationFixture(fixtureCorpus, scenario, config, provider, context);
            long start = System.nanoTime();
            fixture.execute();
            long duration = (System.nanoTime() - start) / 1_000_000;
            var checks = fixture.check(provider != null);
            AiTutorMemoryEvaluation.checkContext(context, fixture.provider.requests, checks);
            if (fixture.provider.requests.isEmpty()) {
                failures.add(condition + ":missing-initial-request");
            } else {
                ModelRequest current = withoutMemory(fixture.provider.requests.getFirst(), context);
                if (invariant == null) { invariant = current; }
                else if (!invariant.equals(current)) { failures.add(condition + ":non-memory-input-drift"); }
            }
            var trial = AiEvaluationReport.result(scenario, fixture, config, provider != null, checks);
            trial.put("condition", condition.name());
            trial.put("memoryContext", JSON.readTree(context));
            trial.put("durationMs", duration);
            trial.put("timingOrigin", provider == null ? "LOCAL_SCRIPTED_HARNESS" : "LOCAL_PROVIDER_ROUND_TRIP");
            trials.add(trial);
        }
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("pairId", sample.id() + "/" + repetition);
        pair.put("caseId", sample.id());
        pair.put("repetition", repetition);
        pair.put("question", sample.question());
        pair.put("activeFactors", sample.activeFactors());
        pair.put("comparisonKind", sample.activeFactors().isEmpty() ? "EMPTY_MEMORY_CONTROL" : "MEMORY_ABLATION");
        pair.put("executionOrder", order);
        pair.put("invariantHash", invariant == null ? null
                : AiEvaluationReport.sha256(JSON.writeValueAsString(invariant)));
        pair.put("pairingFailures", failures);
        pair.put("contractStatus", failures.isEmpty() && trials.stream()
                .allMatch(trial -> "PASS".equals(trial.get("contractStatus"))) ? "PASS" : "FAIL");
        pair.put("manualCriteria", sample.manualCriteria());
        pair.put("comparisonStatus", provider == null ? "NOT_EVALUATED" : "NOT_REVIEWED");
        pair.put("trials", trials);
        pair.put("differencesFromNoMemory", provider == null || sample.activeFactors().isEmpty()
                ? null : differences(trials, failures.isEmpty()));
        return pair;
    }

    static List<AiMemoryAblationCorpus.Condition> order(int index, int repetition) {
        var order = new ArrayList<>(Arrays.asList(AiMemoryAblationCorpus.Condition.values()));
        Collections.rotate(order, -(index + repetition - 1) % order.size());
        return List.copyOf(order);
    }

    static ModelRequest withoutMemory(ModelRequest request, String context) {
        String block = "当前课程的用户记忆（自述与服务端证据分列）：\n" + context;
        return new ModelRequest(request.messages().stream().filter(message ->
                !(message.role() == ModelRequest.Role.USER && block.equals(message.content()))).toList(),
                request.options(), request.tools(), request.outputSchema());
    }

    private static List<Map<String, Object>> differences(List<Map<String, Object>> trials, boolean pairingIntact) {
        var baseline = trials.stream().filter(trial -> "NO_MEMORY".equals(trial.get("condition")))
                .findFirst().orElseThrow();
        List<Map<String, Object>> deltas = new ArrayList<>();
        for (var trial : trials) {
            if (trial == baseline) { continue; }
            boolean passed = pairingIntact && "PASS".equals(baseline.get("contractStatus"))
                    && "PASS".equals(trial.get("contractStatus"));
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("condition", trial.get("condition"));
            delta.put("status", passed ? "OBSERVED_ONLY" : "INCOMPLETE_CONTRACT");
            delta.put("durationMs", passed ? (Long) trial.get("durationMs") - (Long) baseline.get("durationMs") : null);
            delta.put("modelCalls", passed ? (Integer) trial.get("modelCalls") - (Integer) baseline.get("modelCalls") : null);
            var usage = (AiTokenUsage) trial.get("usage");
            var baselineUsage = (AiTokenUsage) baseline.get("usage");
            delta.put("totalTokens", passed && usage != null && baselineUsage != null
                    ? usage.totalTokens() - baselineUsage.totalTokens() : null);
            var cost = (BigDecimal) trial.get("costUsd");
            var baselineCost = (BigDecimal) baseline.get("costUsd");
            delta.put("costUsd", passed && cost != null && baselineCost != null ? cost.subtract(baselineCost) : null);
            deltas.add(delta);
        }
        return deltas;
    }

    private static String implementationHash() throws IOException {
        List<String> hashes = new ArrayList<>();
        for (Class<?> type : List.of(AiMemoryAblationEvaluation.class, AiMemoryAblationCorpus.class,
                AiMemoryAblationCorpus.Condition.class, AiEvaluationFixture.class, AiEvaluationProvider.class,
                AiTutorMemoryEvaluation.class, AiTutorHintEvaluation.class, AiTutorPracticeEvaluation.class,
                AiTutorPlanEvaluation.class, com.learnplatform.service.tutor.TutorAgentRuntime.class)) {
            try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                if (input == null) { throw new IOException("Missing evaluation implementation"); }
                hashes.add(AiEvaluationReport.sha256(java.util.Base64.getEncoder().encodeToString(input.readAllBytes())));
            }
        }
        return AiEvaluationReport.sha256(String.join("\n", hashes));
    }

    static Path write(Map<String, Object> report, Path root) throws IOException {
        Path directory = root.resolve(report.get("mode").toString().toLowerCase(java.util.Locale.ROOT))
                .resolve(report.get("experimentId").toString());
        Files.createDirectories(directory);
        Path output = directory.resolve("report.json");
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        return output;
    }
}
