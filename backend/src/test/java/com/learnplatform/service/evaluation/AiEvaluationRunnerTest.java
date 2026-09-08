package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.config.AiConfig;
import com.learnplatform.service.ai.AiTokenUsage;
import com.learnplatform.service.ai.OpenAiProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class AiEvaluationRunnerTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void environmentBindingRequiresExplicitApplicationConfiguration() {
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
        assertEquals(false, config.isStreamIncludeUsage());
        assertEquals("https://evaluation.invalid/v1", config.getApiBaseUrl());
    }

    @Test
    void synchronousProviderRetainsOnlyUpstreamUsageAndGovernanceMetadata() throws Exception {
        var config = AiEvaluationOnlineTest.configuration(environment());
        var server = new AtomicReference<MockRestServiceServer>();
        var provider = provider(config, server);
        var corpus = AiEvaluationCorpus.load();
        var sample = corpus.cases().stream().filter(c -> c.id().equals("variant-private-answer"))
                .findFirst().orElseThrow();
        server.get().expect(requestTo("https://evaluation.invalid/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("replaceable-test-model"))
                .andExpect(jsonPath("$.temperature").value(0.7))
                .andRespond(withSuccess(json.writeValueAsString(Map.of("choices", new Object[]{Map.of(
                        "message", Map.of("content", sample.response()))},
                        "usage", Map.of("prompt_tokens", 12, "completion_tokens", 8, "total_tokens", 20))),
                        MediaType.APPLICATION_JSON));
        var fixture = new AiEvaluationFixture(corpus, sample, config, provider);
        fixture.execute();
        assertTrue(fixture.check(true).isEmpty(), () -> fixture.check(true).toString());
        var report = AiEvaluationReport.result(sample, fixture, config, true, fixture.check(true));
        assertEquals(new AiTokenUsage(12, 8, 20), report.get("usage"));
        assertNull(report.get("costUsd"));
        assertEquals("NOT_REVIEWED", report.get("teachingQuality"));
        assertEquals(20, fixture.logs.get(0).getTokensUsed());
        assertEquals(64, ((String) report.get("modelConfigVersion")).length());
        server.get().verify();
        server.get().reset();
        server.get().expect(requestTo("https://evaluation.invalid/v1/chat/completions"))
                .andRespond(withSuccess(json.writeValueAsString(Map.of("choices", new Object[]{Map.of(
                        "message", Map.of("content", sample.response()))})), MediaType.APPLICATION_JSON));
        var noUsage = new AiEvaluationFixture(corpus, sample, config, provider);
        noUsage.execute();
        assertEquals(0, noUsage.actualCode);
        assertNull(noUsage.provider.getLastTokenUsage());
        assertNull(noUsage.logs.get(0).getTokensUsed());
        server.get().verify();
        server.get().reset();
        server.get().expect(requestTo("https://evaluation.invalid/v1/chat/completions"))
                .andRespond(withServerError());
        var failed = new AiEvaluationFixture(corpus, sample, config, provider);
        failed.execute();
        assertEquals(1005, failed.actualCode);
        assertTrue(failed.assets.isEmpty());
        assertEquals(0, failed.logs.get(0).getStatus());
        assertNull(failed.provider.getLastTokenUsage());
        server.get().verify();
    }

    @Test
    void streamedProviderUsesProductionPromptAndFinalUsage() throws Exception {
        var config = AiEvaluationOnlineTest.configuration(environment());
        var server = new AtomicReference<MockRestServiceServer>();
        var provider = provider(config, server);
        var corpus = AiEvaluationCorpus.load();
        var sample = corpus.cases().stream().filter(c -> c.id().equals("paper-wrong-attempt"))
                .findFirst().orElseThrow();
        String stream = "data: " + json.writeValueAsString(Map.of("choices", new Object[]{Map.of(
                "delta", Map.of("content", sample.response()))})) + "\n\n"
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8,\"total_tokens\":20}}\n\n"
                + "data: [DONE]\n\n";
        server.get().expect(requestTo("https://evaluation.invalid/v1/chat/completions"))
                .andExpect(jsonPath("$.stream").value(true))
                .andExpect(jsonPath("$.stream_options.include_usage").value(true))
                .andRespond(withSuccess(stream, MediaType.TEXT_EVENT_STREAM));
        var fixture = new AiEvaluationFixture(corpus, sample, config, provider);
        fixture.execute();
        assertTrue(fixture.check(true).isEmpty(), () -> fixture.check(true).toString());
        var report = AiEvaluationReport.result(sample, fixture, config, true, fixture.check(true));
        assertEquals(new AiTokenUsage(12, 8, 20), report.get("usage"));
        assertEquals(fixture.logs.get(0).getPromptHash(), report.get("promptHash"));
        server.get().verify();
    }

    private OpenAiProvider provider(AiConfig config, AtomicReference<MockRestServiceServer> server) {
        return new OpenAiProvider(config, new RestTemplateBuilder().additionalCustomizers(
                template -> server.set(MockRestServiceServer.bindTo(template).build())), json);
    }

    private Map<String, Object> environment() {
        return new LinkedHashMap<>(Map.of("AI_ENABLED", "true", "AI_API_KEY", "test-placeholder",
                "AI_MODEL", "replaceable-test-model", "AI_API_BASE_URL", "https://evaluation.invalid/v1"));
    }
}
