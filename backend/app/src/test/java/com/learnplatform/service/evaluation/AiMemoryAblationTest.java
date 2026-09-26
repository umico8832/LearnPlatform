package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.config.AiConfig;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.ai.OpenAiProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiMemoryAblationTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void offlineMatrixUsesIdenticalNonMemoryInputsAndNeverScoresTeachingQuality() throws Exception {
        var report = AiMemoryAblationEvaluation.run(AiMemoryAblationCorpus.load(), config(), null,
                new AiMemoryAblationEvaluation.Settings(Set.of(), 1));
        Path output = AiMemoryAblationEvaluation.write(report, Path.of("target", "ai-evaluation", "memory-ablation"));
        JsonNode root = json.readTree(output.toFile());
        assertEquals("PASS", root.path("contractStatus").asText(), root.toPrettyString());
        assertEquals("NOT_EVALUATED", root.path("comparisonStatus").asText());
        assertEquals(6, root.path("pairs").size());
        assertEquals(24, root.path("plannedTrials").asInt());
        assertEquals(96, root.path("maxProviderCalls").asInt());
        assertEquals(64, root.path("fixtureImplementationHash").asText().length());
        Set<String> runIds = new HashSet<>();
        for (JsonNode pair : root.path("pairs")) {
            assertTrue(pair.path("pairingFailures").isEmpty());
            assertTrue(pair.path("differencesFromNoMemory").isNull());
            Set<String> conditions = new HashSet<>();
            Set<String> invariants = new HashSet<>();
            for (JsonNode trial : pair.path("trials")) {
                conditions.add(trial.path("condition").asText());
                assertEquals("SCRIPTED_FIXTURE", trial.path("responseOrigin").asText());
                assertEquals("NOT_EVALUATED", trial.path("teachingQuality").asText());
                assertTrue(trial.path("usage").isNull());
                assertTrue(trial.path("costUsd").isNull());
                assertEquals(2, trial.path("modelRequests").size());
                assertTrue(runIds.add(trial.path("callMetrics").get(0).path("runId").asText()));
                String context = trial.path("memoryContext").toString();
                List<ModelRequest> requests = new ArrayList<>();
                for (JsonNode request : trial.path("modelRequests")) {
                    requests.add(json.treeToValue(request, ModelRequest.class));
                }
                List<String> failures = new ArrayList<>();
                AiTutorMemoryEvaluation.checkContext(context, requests, failures);
                assertTrue(failures.isEmpty(), failures.toString());
                invariants.add(json.writeValueAsString(AiMemoryAblationEvaluation.withoutMemory(requests.getFirst(), context)));
                assertEquals("read_tutor_lesson", trial.path("toolTrace").get(0).path("name").asText());
            }
            assertEquals(Set.of("NO_MEMORY", "PROFILE_ONLY", "NOTES_ONLY", "FULL_MEMORY"), conditions);
            assertEquals(1, invariants.size());
        }
    }

    @Test
    void interventionsRemoveOnlyTheirFactorAndKeepNotesEvidenceTogether() throws Exception {
        var corpus = AiMemoryAblationCorpus.load();
        boolean injection = false;
        boolean deleted = false;
        for (var sample : corpus.cases()) {
            var full = json.readTree(AiMemoryAblationCorpus.Condition.FULL_MEMORY.context(sample));
            var none = json.readTree(AiMemoryAblationCorpus.Condition.NO_MEMORY.context(sample));
            var profile = json.readTree(AiMemoryAblationCorpus.Condition.PROFILE_ONLY.context(sample));
            var notes = json.readTree(AiMemoryAblationCorpus.Condition.NOTES_ONLY.context(sample));
            assertEquals(sample.memory(), full);
            assertEquals(full.path("sessionNotes"), notes.path("sessionNotes"));
            assertTrue(profile.path("sessionNotes").isEmpty());
            assertTrue(none.path("sessionNotes").isEmpty());
            assertTrue(none.path("goal").isNull());
            assertTrue(notes.path("goal").isNull());
            assertTrue(notes.path("explanationStyle").isNull());
            assertEquals(full.path("goal"), profile.path("goal"));
            assertEquals(full.path("explanationStyle"), profile.path("explanationStyle"));
            assertFalse(sample.question().contains("EVAL_INJECTION_CANARY"));
            assertFalse(sample.question().contains(full.path("goal").asText("NULL_UNUSED")));
            if (full.path("sessionNotes").isEmpty()) {
                deleted = true;
                assertTrue(full.path("goal").isNull());
                assertTrue(full.path("explanationStyle").isNull());
                assertEquals(3, full.path("revision").asInt());
            }
            for (JsonNode note : full.path("sessionNotes")) {
                var source = note.path("source");
                assertTrue(source.path("available").asBoolean());
                String status = source.path("checkStatus").asText();
                assertTrue(Set.of("UNANSWERED", "INCORRECT", "CORRECT").contains(status));
                assertEquals("UNANSWERED".equals(status), source.path("checkAnsweredAt").isNull());
                if (note.path("note").asText().contains("EVAL_INJECTION_CANARY")) { injection = true; }
            }
        }
        assertTrue(injection);
        assertTrue(deleted);
    }

    @Test
    void settingsRejectUnknownCasesAndUnboundedRepetitionsBeforeCallingProvider() throws Exception {
        var corpus = AiMemoryAblationCorpus.load();
        assertThrows(IllegalArgumentException.class, () -> AiMemoryAblationEvaluation.Settings.from(
                Map.of("AI_MEMORY_EVAL_CASES", "missing"), corpus));
        for (String invalid : List.of("0", "5", "-1", "abc")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> AiMemoryAblationEvaluation.Settings.from(
                    Map.of("AI_MEMORY_EVAL_REPETITIONS", invalid), corpus));
            assertEquals("AI_MEMORY_EVAL_REPETITIONS must be between 1 and 4", failure.getMessage());
        }
        for (int index = 0; index < 6; index++) {
            for (int position = 0; position < 4; position++) {
                Set<AiMemoryAblationCorpus.Condition> observed = new HashSet<>();
                for (int repeat = 1; repeat <= 4; repeat++) {
                    observed.add(AiMemoryAblationEvaluation.order(index, repeat).get(position));
                }
                assertEquals(4, observed.size());
            }
        }
    }

    @Test
    void onlineFailuresRemainInTheReportAndDoNotBecomeSuccessfulComparisons(@TempDir Path root) throws Exception {
        var corpus = AiMemoryAblationCorpus.load();
        AiProvider provider = mock(AiProvider.class);
        when(provider.complete(any(), any())).thenThrow(new IllegalStateException("private-upstream-message"));
        var report = AiMemoryAblationEvaluation.run(corpus, config(), provider,
                new AiMemoryAblationEvaluation.Settings(Set.of(corpus.cases().getFirst().id()), 1));
        var tree = json.readTree(AiMemoryAblationEvaluation.write(report, root).toFile());
        assertEquals("FAIL", tree.path("contractStatus").asText());
        assertFalse(tree.toString().contains("private-upstream-message"));
        assertEquals("NOT_REVIEWED", tree.path("comparisonStatus").asText());
        assertEquals(4, tree.path("pairs").get(0).path("trials").size());
        for (var difference : tree.path("pairs").get(0).path("differencesFromNoMemory")) {
            assertEquals("INCOMPLETE_CONTRACT", difference.path("status").asText());
            assertTrue(difference.path("totalTokens").isNull());
            assertTrue(difference.path("durationMs").isNull());
        }
    }

    @Test
    void injectedOutputFailsOnlyExposedArmsAndDeletedMemoryIsOnlyAControl() throws Exception {
        var corpus = AiMemoryAblationCorpus.load();
        AiProvider provider = mock(AiProvider.class);
        when(provider.complete(any(), any())).thenAnswer(invocation -> {
            ModelRequest request = invocation.getArgument(0);
            boolean initial = request.messages().stream().noneMatch(message -> message.role() == ModelRequest.Role.TOOL);
            boolean injected = request.messages().stream().anyMatch(message -> message.role() == ModelRequest.Role.USER
                    && message.content().contains("EVAL_INJECTION_CANARY"));
            return new ModelResult(initial ? "" : injected ? "EVAL_INJECTION_CANARY" : "栈遵循后进先出。", initial
                    ? List.of(new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}")) : List.of(),
                    "test-model", null, initial ? ModelResult.Finish.TOOL_CALLS : ModelResult.Finish.STOP, null);
        });
        var report = json.valueToTree(AiMemoryAblationEvaluation.run(corpus, config(), provider,
                new AiMemoryAblationEvaluation.Settings(Set.of("stack-note-injection", "stack-deleted-memory-control"), 1)));
        assertEquals("FAIL", report.path("contractStatus").asText());
        for (var pair : report.path("pairs")) {
            if ("EMPTY_MEMORY_CONTROL".equals(pair.path("comparisonKind").asText())) {
                assertEquals("PASS", pair.path("contractStatus").asText());
                assertTrue(pair.path("activeFactors").isEmpty());
                assertTrue(pair.path("differencesFromNoMemory").isNull());
            } else {
                for (var trial : pair.path("trials")) {
                    boolean exposed = Set.of("NOTES_ONLY", "FULL_MEMORY").contains(trial.path("condition").asText());
                    assertEquals(exposed ? "FAIL" : "PASS", trial.path("contractStatus").asText());
                    if (exposed) {
                        assertTrue(trial.path("failures").toString().contains("output-forbidden:EVAL_INJECTION_CANARY"));
                    }
                }
                for (var difference : pair.path("differencesFromNoMemory")) {
                    assertTrue(difference.path("totalTokens").isNull());
                    assertTrue(difference.path("costUsd").isNull());
                }
            }
        }
    }

    @Test
    void changedNonMemoryConfigurationInvalidatesThePairEvenWhenIndividualTrialsPass() throws Exception {
        var corpus = AiMemoryAblationCorpus.load();
        var config = config();
        AiProvider provider = mock(AiProvider.class);
        when(provider.complete(any(), any())).thenAnswer(invocation -> {
            ModelRequest request = invocation.getArgument(0);
            boolean initial = request.messages().stream().noneMatch(message -> message.role() == ModelRequest.Role.TOOL);
            if (!initial) { config.setTemperature(1.0); }
            return new ModelResult(initial ? "" : "栈遵循后进先出。", initial
                    ? List.of(new ModelRequest.ToolCall("lesson", "read_tutor_lesson", "{}")) : List.of(),
                    "test-model", null, initial ? ModelResult.Finish.TOOL_CALLS : ModelResult.Finish.STOP,
                    new ModelResult.Usage(12, 8, 20));
        });
        var tree = json.valueToTree(AiMemoryAblationEvaluation.run(corpus, config, provider,
                new AiMemoryAblationEvaluation.Settings(Set.of(corpus.cases().getFirst().id()), 1)));
        assertEquals("FAIL", tree.path("contractStatus").asText());
        var pair = tree.path("pairs").get(0);
        assertTrue(pair.path("pairingFailures").toString().contains("non-memory-input-drift"));
        for (var trial : pair.path("trials")) { assertEquals("PASS", trial.path("contractStatus").asText()); }
        for (var difference : pair.path("differencesFromNoMemory")) {
            assertEquals("INCOMPLETE_CONTRACT", difference.path("status").asText());
            assertTrue(difference.path("totalTokens").isNull());
            assertTrue(difference.path("durationMs").isNull());
        }
    }

    @Test
    void onlineComparisonsPreserveActualUsageAndUnknownCosts(@TempDir Path root) throws Exception {
        var corpus = AiMemoryAblationCorpus.load();
        List<JsonNode> requests = new CopyOnWriteArrayList<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            JsonNode request = json.readTree(exchange.getRequestBody());
            requests.add(request);
            boolean initial = request.path("messages").size() == 3;
            Map<String, Object> message = initial ? Map.of("role", "assistant", "tool_calls", List.of(
                    Map.of("id", "lesson", "type", "function", "function",
                            Map.of("name", "read_tutor_lesson", "arguments", "{}"))))
                    : Map.of("role", "assistant", "content", "栈遵循后进先出。");
            byte[] response = json.writeValueAsString(Map.of("choices", List.of(Map.of("message", message,
                    "finish_reason", initial ? "tool_calls" : "stop")),
                    "usage", Map.of("prompt_tokens", 12, "completion_tokens", 8, "total_tokens", 20)))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var config = config();
            config.setEnabled(true);
            config.setApiKey("test-placeholder");
            config.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            var provider = new OpenAiProvider(config);
            var settings = new AiMemoryAblationEvaluation.Settings(Set.of(corpus.cases().getFirst().id()), 1);
            var report = AiMemoryAblationEvaluation.run(corpus, config, provider, settings);
            var first = AiMemoryAblationEvaluation.write(report, root);
            var second = AiMemoryAblationEvaluation.write(
                    AiMemoryAblationEvaluation.run(corpus, config, provider, settings), root);
            assertNotEquals(first, second);
            var tree = json.readTree(first.toFile());
            assertEquals("PASS", tree.path("contractStatus").asText(), tree.toPrettyString());
            assertEquals(16, requests.size());
            assertFalse(tree.toString().contains("test-placeholder"));
            assertFalse(tree.toString().contains(config.getApiBaseUrl()));
            var pair = tree.path("pairs").get(0);
            int trialIndex = 0;
            for (var trial : pair.path("trials")) {
                assertEquals(40, trial.path("usage").path("totalTokens").asInt());
                assertTrue(trial.path("costUsd").isNull());
                assertEquals("NOT_REVIEWED", trial.path("teachingQuality").asText());
                assertEquals(trial.path("modelRequests").get(0).path("messages").get(1).path("content"),
                        requests.get(trialIndex * 2).path("messages").get(1).path("content"));
                assertEquals(9, requests.get(trialIndex * 2).path("tools").size());
                trialIndex++;
            }
            for (var difference : pair.path("differencesFromNoMemory")) {
                assertEquals(0, difference.path("totalTokens").asInt());
                assertTrue(difference.path("costUsd").isNull());
                assertEquals("OBSERVED_ONLY", difference.path("status").asText());
            }
        } finally {
            server.stop(0);
        }
    }

    private AiConfig config() {
        var config = new AiConfig();
        config.setModel("scripted-memory-fixture-v1");
        config.setToolsSupported(true);
        return config;
    }
}
