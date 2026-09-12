package com.learnplatform.service.evaluation;

import com.learnplatform.config.AiConfig;
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
            assertTrue(Set.of("ASSET", "PAPER").contains(sample.route()), sample.id());
            assertFalse(sample.online() && sample.expectedCode() != 0, sample.id());
            assertTrue(Set.of("NORMAL", "CORRECT_ANSWER", "ANSWER_INJECTION", "UNANSWERED",
                    "OUTSIDE_SESSION", "FOREIGN_OWNER", "QUOTA", "UPSTREAM_ERROR", "EMPTY_STREAM",
                    "SELF_REVIEW").contains(sample.scenario()), sample.id());
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

    @AfterAll
    static void writeReport() throws Exception {
        RESULTS.sort(java.util.Comparator.comparing(row -> (String) row.get("caseId")));
        AiEvaluationReport.write(AiEvaluationCorpus.load(), CONFIG, false, RESULTS);
    }

    private static AiConfig offlineConfig() {
        AiConfig config = new AiConfig();
        config.setModel("scripted-fixture-v1");
        return config;
    }
}
