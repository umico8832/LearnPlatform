package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.config.AiConfig;
import com.learnplatform.service.ai.AiTokenUsage;
import com.learnplatform.service.ai.OpenAiProvider;
import com.learnplatform.support.TestCloudServer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiEvaluationRunnerTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void environmentBindingRequiresExplicitApplicationConfiguration() {
        assertThrows(AssertionError.class, () -> AiEvaluationOnlineTest.configuration(Map.of()));
        var missingKey = environment();
        missingKey.remove("AI_API_KEY");
        assertThrows(AssertionError.class, () -> AiEvaluationOnlineTest.configuration(missingKey));
        var environment = environment();
        environment.put("AI_MAX_TOKENS", "777");
        environment.put("AI_STREAM_INCLUDE_USAGE", "false");
        AiConfig config = AiEvaluationOnlineTest.configuration(environment);
        assertEquals("replaceable-test-model", config.getModel());
        assertEquals(777, config.getMaxTokens());
        assertFalse(config.isStreamIncludeUsage());
        assertEquals("https://evaluation.invalid/v1", config.getApiBaseUrl());
    }

    @Test void synchronousProviderRetainsOnlyUpstreamUsageAndGovernanceMetadata() throws Exception {
        try (var server = new TestCloudServer()) {
            var config = AiEvaluationOnlineTest.configuration(environment());
            config.setApiBaseUrl(server.url());
            var provider = new OpenAiProvider(config);
            var corpus = AiEvaluationCorpus.load();
            var sample = corpus.cases().stream().filter(c -> c.id().equals("variant-private-answer"))
                    .findFirst().orElseThrow();
            var choices = new Object[]{Map.of("message", Map.of("role", "assistant", "content", sample.response()),
                    "finish_reason", "stop")};
            server.response = json.writeValueAsString(Map.of("choices", choices,
                    "usage", Map.of("prompt_tokens", 12, "completion_tokens", 8, "total_tokens", 20)));
            var fixture = new AiEvaluationFixture(corpus, sample, config, provider);
            fixture.execute();
            assertTrue(fixture.check(true).isEmpty(), () -> fixture.check(true).toString());
            var report = AiEvaluationReport.result(sample, fixture, config, true, fixture.check(true));
            assertEquals(new AiTokenUsage(12, 8, 20), report.get("usage"));
            assertNull(report.get("costUsd"));
            assertEquals("NOT_REVIEWED", report.get("teachingQuality"));
            assertEquals(20, fixture.logs.get(0).getTokensUsed());
            assertEquals(64, ((String) report.get("modelConfigVersion")).length());
            assertEquals("replaceable-test-model", json.readTree(server.request).path("model").asText());
            server.response = json.writeValueAsString(Map.of("choices", choices));
            var noUsage = new AiEvaluationFixture(corpus, sample, config, provider);
            noUsage.execute();
            assertEquals(0, noUsage.actualCode);
            assertNull(noUsage.provider.usage());
            assertNull(noUsage.logs.get(0).getTokensUsed());
            server.status = 500;
            server.response = "upstream failure";
            var failed = new AiEvaluationFixture(corpus, sample, config, provider);
            failed.execute();
            assertEquals(1005, failed.actualCode);
            assertTrue(failed.assets.isEmpty());
            assertEquals(0, failed.logs.get(0).getStatus());
            assertNull(failed.provider.usage());
            assertEquals(3, server.calls);
        }
    }

    @Test void streamedProviderUsesProductionPromptAndFinalUsage() throws Exception {
        try (var server = new TestCloudServer()) {
            var config = AiEvaluationOnlineTest.configuration(environment());
            config.setApiBaseUrl(server.url());
            var corpus = AiEvaluationCorpus.load();
            var sample = corpus.cases().stream().filter(c -> c.id().equals("paper-wrong-attempt"))
                    .findFirst().orElseThrow();
            server.contentType = "text/event-stream";
            server.response = "data: " + json.writeValueAsString(Map.of("choices", new Object[]{Map.of(
                    "index", 0, "delta", Map.of("content", sample.response()))})) + "\n\n"
                    + "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8,\"total_tokens\":20}}\n\n"
                    + "data: [DONE]\n\n";
            var fixture = new AiEvaluationFixture(corpus, sample, config, new OpenAiProvider(config));
            fixture.execute();
            assertTrue(fixture.check(true).isEmpty(), () -> fixture.check(true).toString());
            var report = AiEvaluationReport.result(sample, fixture, config, true, fixture.check(true));
            assertEquals(new AiTokenUsage(12, 8, 20), report.get("usage"));
            assertEquals(fixture.logs.get(0).getPromptHash(), report.get("promptHash"));
            assertTrue(json.readTree(server.request).path("stream_options").path("include_usage").asBoolean());
        }
    }
    private Map<String, Object> environment() {
        return new LinkedHashMap<>(Map.of("AI_ENABLED", "true", "AI_API_KEY", "test-placeholder",
                "AI_MODEL", "replaceable-test-model", "AI_API_BASE_URL", "https://evaluation.invalid/v1"));
    }
}
