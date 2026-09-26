package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.config.AiConfig;
import com.learnplatform.service.ai.AiTokenUsage;
import com.learnplatform.service.ai.OpenAiProvider;
import com.learnplatform.support.TestCloudServer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;

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

    @Test void ragProviderRoundTripPreservesToolEvidenceAndAggregatesActualUsage() throws Exception {
        var corpus = AiEvaluationCorpus.load();
        var sample = corpus.cases().stream().filter(item -> "RAG_INJECTION".equals(item.scenario()))
                .findFirst().orElseThrow();
        var toolCalls = List.of(
                Map.of("id", "lesson-1", "type", "function", "function",
                        Map.of("name", "read_tutor_lesson", "arguments", "{}")),
                Map.of("id", "knowledge-1", "type", "function", "function",
                        Map.of("name", "search_course_knowledge", "arguments", "{\"query\":\"栈\"}")));
        List<String> responses = List.of(
                json.writeValueAsString(Map.of("choices", List.of(Map.of("message",
                        Map.of("role", "assistant", "tool_calls", toolCalls), "finish_reason", "tool_calls")),
                        "usage", Map.of("prompt_tokens", 12, "completion_tokens", 8, "total_tokens", 20))),
                json.writeValueAsString(Map.of("choices", List.of(Map.of("message",
                        Map.of("role", "assistant", "content", sample.response()), "finish_reason", "stop")),
                        "usage", Map.of("prompt_tokens", 30, "completion_tokens", 10, "total_tokens", 40))));
        var requests = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int index = requests.size() - 1;
            byte[] body = (index < responses.size() ? responses.get(index) : "{}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(index < responses.size() ? 200 : 500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var config = AiEvaluationOnlineTest.configuration(environment());
            config.setToolsSupported(true);
            config.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            var fixture = new AiEvaluationFixture(corpus, sample, config, new OpenAiProvider(config));
            fixture.execute();
            assertTrue(fixture.check(true).isEmpty(), () -> fixture.check(true).toString());
            assertEquals(2, requests.size());
            var second = json.readTree(requests.get(1));
            var knowledge = second.path("messages").get(second.path("messages").size() - 1);
            assertEquals("tool", knowledge.path("role").asText());
            assertEquals("knowledge-1", knowledge.path("tool_call_id").asText());
            assertTrue(knowledge.path("content").asText().contains("EVAL_INJECTION_CANARY"));
            assertFalse(second.path("messages").get(0).path("content").asText().contains("EVAL_INJECTION_CANARY"));
            var report = AiEvaluationReport.result(sample, fixture, config, true, fixture.check(true));
            assertEquals(new AiTokenUsage(42, 18, 60), report.get("usage"));
            assertEquals("SYNTHETIC_TOOL_FIXTURE", report.get("retrievalOrigin"));
            assertEquals("NOT_REVIEWED", report.get("teachingQuality"));
        } finally {
            server.stop(0);
        }
    }

    private Map<String, Object> environment() {
        return new LinkedHashMap<>(Map.of("AI_ENABLED", "true", "AI_API_KEY", "test-placeholder",
                "AI_MODEL", "replaceable-test-model", "AI_API_BASE_URL", "https://evaluation.invalid/v1"));
    }
}
