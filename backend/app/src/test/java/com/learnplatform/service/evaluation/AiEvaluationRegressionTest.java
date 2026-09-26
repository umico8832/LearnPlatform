package com.learnplatform.service.evaluation;

import com.learnplatform.config.AiConfig;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.service.ai.AiProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiEvaluationRegressionTest {
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final AiConfig CONFIG = offlineConfig();

    @TestFactory
    Stream<DynamicTest> fixedCases() throws Exception {
        AiEvaluationCorpus corpus = AiEvaluationCorpus.load();
        return corpus.cases().stream().map(sample -> DynamicTest.dynamicTest(sample.id(), () -> {
            AiEvaluationFixture fixture = new AiEvaluationFixture(corpus, sample, CONFIG, null);
            fixture.execute();
            List<String> failures = fixture.check(false);
            RESULTS.add(AiEvaluationReport.result(sample, fixture, CONFIG, false, failures));
            assertTrue(failures.isEmpty(), () -> sample.id() + ": " + failures);
        }));
    }

    @Test
    void corpusHasUniqueCasesAndExplicitHumanCriteria() throws Exception {
        AiEvaluationCorpus corpus = AiEvaluationCorpus.load();
        Set<String> ids = new HashSet<>();
        Set<String> categories = new HashSet<>();
        for (var sample : corpus.cases()) {
            assertTrue(ids.add(sample.id()), sample.id());
            assertTrue(corpus.questions().containsKey(sample.question()), sample.id());
            assertFalse(sample.manualCriteria().isEmpty(), sample.id());
            assertTrue(Set.of("ASSET", "PAPER", "AGENT").contains(sample.route()), sample.id());
            assertFalse(sample.online() && sample.expectedCode() != 0, sample.id());
            assertTrue(Set.of("NORMAL", "CORRECT_ANSWER", "ANSWER_INJECTION", "UNANSWERED",
                    "OUTSIDE_SESSION", "FOREIGN_OWNER", "QUOTA", "UPSTREAM_ERROR", "EMPTY_STREAM",
                    "SELF_REVIEW", "LEARNING_EVIDENCE", "RAG_FOUND", "RAG_EMPTY", "RAG_INJECTION",
                    "TUTOR_CHECK_UNANSWERED", "TUTOR_CHECK_ANSWERED", "TUTOR_CHECK_SELF_CLAIM",
                    "TUTOR_HINT_FIRST", "TUTOR_HINT_ANSWERED")
                    .contains(sample.scenario()), sample.id());
            if (sample.usesRetrieval()) {
                assertEquals("AGENT", sample.route());
                assertEquals("RAG_EMPTY".equals(sample.scenario()), sample.retrieval().isEmpty());
                sample.retrieval().forEach(citation -> assertEquals(
                        AiEvaluationReport.sha256(citation.text()), citation.contentHash()));
            }
            categories.add(sample.category());
        }
        assertEquals(Set.of("knowledge", "structured", "answer-boundary", "course-scope",
                "contradiction", "prompt-injection", "permission", "upstream"), categories);
    }

    @Test
    void reportNeverLabelsScriptedResponseAsTeachingEvidenceOrRealUsage() throws Exception {
        var corpus = AiEvaluationCorpus.load();
        var sample = corpus.cases().get(0);
        var fixture = new AiEvaluationFixture(corpus, sample, CONFIG, null);
        fixture.execute();
        var report = AiEvaluationReport.result(sample, fixture, CONFIG, false, fixture.check(false));
        assertEquals("NOT_EVALUATED", report.get("teachingQuality"));
        assertEquals("SCRIPTED_FIXTURE", report.get("responseOrigin"));
        assertEquals(null, report.get("usage"));
        assertEquals(null, report.get("costUsd"));
        assertEquals(64, ((String) report.get("promptHash")).length());
        fixture.provider.response = "EVAL_INJECTION_CANARY";
        assertTrue(fixture.check(false).contains("output-forbidden:EVAL_INJECTION_CANARY"));
        fixture.provider.response = null;
        assertTrue(fixture.check(false).contains("nonempty-response"));
    }

    @Test
    void corpusIncludesRetrievalSuccessEmptyAndInjectionCases() throws Exception {
        var corpus = AiEvaluationCorpus.load();
        for (String scenario : List.of("RAG_FOUND", "RAG_EMPTY", "RAG_INJECTION")) {
            var sample = corpus.cases().stream().filter(item -> scenario.equals(item.scenario()))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing " + scenario));
            var fixture = new AiEvaluationFixture(corpus, sample, CONFIG, null);
            fixture.execute();
            assertTrue(fixture.check(false).isEmpty(), () -> fixture.check(false).toString());
            assertTrue(fixture.agentTools.contains("search_course_knowledge"));
            assertEquals(!"RAG_EMPTY".equals(scenario), fixture.publicOutput.contains("本轮检索资料："));
        }
    }

    @Test
    void retrievalChecksDetectMissingSourcesContextAndRunIdentity() throws Exception {
        var corpus = AiEvaluationCorpus.load();
        var sample = corpus.cases().stream().filter(item -> "RAG_FOUND".equals(item.scenario()))
                .findFirst().orElseThrow();
        var fixture = new AiEvaluationFixture(corpus, sample, CONFIG, null);
        fixture.execute();
        fixture.publicOutput = fixture.provider.response;
        assertTrue(fixture.check(false).contains("retrieval-server-source-suffix"));
        fixture.publicOutput = "EVAL_INJECTION_CANARY";
        assertTrue(fixture.check(false).contains("retrieval-public-injection"));
        int position = fixture.toolTrace.size() - 1;
        var trace = fixture.toolTrace.get(position);
        fixture.toolTrace.set(position, new AiEvaluationFixture.ToolObservation(
                trace.callId(), trace.name(), trace.arguments(), "unseen-tool-output", java.util.UUID.randomUUID()));
        assertTrue(fixture.check(false).contains("retrieval-stable-run-id"));
        assertTrue(fixture.check(false).contains("retrieval-tool-context"));
        fixture.agentTools.remove("search_course_knowledge");
        assertTrue(fixture.check(false).contains("agent-search-course-knowledge"));
    }

    @Test
    void prematureRetrievalIsAuditedAsProtocolFailureWithoutToolExecution() throws Exception {
        var corpus = AiEvaluationCorpus.load();
        var sample = corpus.cases().stream().filter(item -> "RAG_FOUND".equals(item.scenario()))
                .findFirst().orElseThrow();
        var delegate = mock(AiProvider.class);
        when(delegate.complete(any(), any())).thenReturn(new ModelResult("", List.of(
                new ModelRequest.ToolCall("search-first", "search_course_knowledge", "{\"query\":\"栈\"}")),
                "test-model", null, ModelResult.Finish.TOOL_CALLS, new ModelResult.Usage(12, 8, 20)));
        var fixture = new AiEvaluationFixture(corpus, sample, CONFIG, delegate);
        fixture.execute();
        assertEquals(-1, fixture.actualCode);
        assertEquals("ModelException", fixture.errorType);
        assertTrue(fixture.toolTrace.isEmpty());
        assertEquals("", fixture.publicOutput);
        assertEquals(1, fixture.logs.size());
        assertEquals("PROTOCOL", fixture.logs.getFirst().getOutcome());
        assertEquals(0, fixture.logs.getFirst().getStatus());
        assertEquals(20, fixture.logs.getFirst().getTokensUsed());
    }

    @Test
    void retrievalReportsDiscloseSyntheticSourcesEvenWithRealProviderMode() throws Exception {
        var corpus = AiEvaluationCorpus.load();
        var sample = corpus.cases().stream().filter(item -> "RAG_INJECTION".equals(item.scenario()))
                .findFirst().orElseThrow();
        var fixture = new AiEvaluationFixture(corpus, sample, CONFIG, null);
        fixture.execute();
        for (boolean online : List.of(false, true)) {
            var report = AiEvaluationReport.result(sample, fixture, CONFIG, online, fixture.check(online));
            assertEquals("SYNTHETIC_TOOL_FIXTURE", report.get("retrievalOrigin"));
            assertEquals("NOT_EVALUATED", report.get("retrievalQuality"));
            assertEquals(fixture.publicOutput, report.get("publicOutput"));
            assertEquals(fixture.toolTrace, report.get("toolTrace"));
            assertTrue(fixture.toolTrace.stream().anyMatch(trace ->
                    trace.output().contains("EVAL_INJECTION_CANARY")));
        }
    }

    @Test
    void corpusIncludesTutorAgentToolAndEvidenceCases() throws Exception {
        var cases = AiEvaluationCorpus.load().cases();
        assertTrue(cases.stream().anyMatch(sample -> "AGENT".equals(sample.route())
                && "NORMAL".equals(sample.scenario())));
        assertTrue(cases.stream().anyMatch(sample -> "AGENT".equals(sample.route())
                && "LEARNING_EVIDENCE".equals(sample.scenario())));
        assertTrue(cases.stream().anyMatch(sample -> "AGENT".equals(sample.route())
                && "prompt-injection".equals(sample.category())));
        var sample = cases.stream().filter(item -> "AGENT".equals(item.route())
                && "LEARNING_EVIDENCE".equals(item.scenario())).findFirst().orElseThrow();
        var fixture = new AiEvaluationFixture(AiEvaluationCorpus.load(), sample, CONFIG, null);
        fixture.execute();
        assertTrue(fixture.check(false).isEmpty(), () -> fixture.check(false).toString());
        assertEquals(2, fixture.logs.size());
        assertEquals(2, AiEvaluationReport.result(sample, fixture, CONFIG, false,
                fixture.check(false)).get("modelCalls"));
    }

    @Test
    void corpusIncludesTutorCheckActionAndServerResultCases() throws Exception {
        var corpus = AiEvaluationCorpus.load();
        for (String scenario : List.of("TUTOR_CHECK_UNANSWERED", "TUTOR_CHECK_ANSWERED", "TUTOR_CHECK_SELF_CLAIM")) {
            var sample = corpus.cases().stream().filter(item -> scenario.equals(item.scenario())).findFirst().orElseThrow();
            var fixture = new AiEvaluationFixture(corpus, sample, CONFIG, null);
            fixture.execute();
            assertTrue(fixture.check(false).isEmpty(), () -> fixture.check(false).toString());
            assertTrue(fixture.publicOutput.contains("\"content\"") && fixture.publicOutput.contains("\"actions\""));
        }
    }

    @Test
    void hintEvaluationChecksTheServerLevelAndKeepsTheEffectiveToolOutput() throws Exception {
        var corpus = AiEvaluationCorpus.load();
        var sample = corpus.cases().stream().filter(item -> "TUTOR_HINT_FIRST".equals(item.scenario()))
                .findFirst().orElseThrow();
        var fixture = new AiEvaluationFixture(corpus, sample, CONFIG, null);
        fixture.execute();
        assertTrue(fixture.check(false).isEmpty(), () -> fixture.check(false).toString());
        var hint = fixture.toolTrace.stream().filter(trace -> "request_tutor_hint".equals(trace.name()))
                .findFirst().orElseThrow();
        assertEquals("hint-1", hint.callId());
        assertTrue(hint.output().contains("\"level\":1"));
        assertTrue(fixture.provider.requests.stream().flatMap(request -> request.messages().stream())
                .anyMatch(message -> hint.callId().equals(message.toolCallId())
                        && hint.output().equals(message.content())));
        fixture.publicOutput = "{\"content\":\"wrong level\",\"actions\":[{\"type\":\"HINT\",\"level\":3}]}";
        assertTrue(fixture.check(false).contains("agent-hint-level"));
    }

    @AfterAll
    static void writeReport() throws Exception {
        RESULTS.sort(java.util.Comparator.comparing(row -> (String) row.get("caseId")));
        AiEvaluationReport.write(AiEvaluationCorpus.load(), CONFIG, false, RESULTS);
    }

    private static AiConfig offlineConfig() {
        AiConfig config = new AiConfig();
        config.setModel("scripted-fixture-v1");
        config.setToolsSupported(true);
        return config;
    }
}
