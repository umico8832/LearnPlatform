package com.learnplatform.service.understanding;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.service.AiCallGovernanceService;
import com.learnplatform.service.AiInvocationService;
import com.learnplatform.service.ai.AiCallContext;
import com.learnplatform.service.ai.AiCallTicket;
import com.learnplatform.service.ai.AiCostCalculator;
import com.learnplatform.service.ai.OpenAiProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionAskEvaluationTest {
    @Test void evaluateBothThinkingModesThroughProductionGatewayAndGuard() throws Exception {
        boolean real = Boolean.getBoolean("questionAskEval.real");
        var json = JsonMapper.builderWithJackson2Defaults().build();
        var config = new AiConfig();
        config.setEnabled(true);
        config.setModel("deepseek-flash");
        var sampleRef = new AtomicReference<JsonNode>();
        var generation = new AtomicInteger();
        HttpServer server = null;
        if (real) {
            assertEquals("true", System.getenv("AI_ENABLED"), "AI_ENABLED must be explicitly true");
            assertEquals("deepseek-flash", System.getenv("AI_MODEL"), "Only deepseek-flash is allowed");
            assertEquals("https://api.deepseek.com", System.getenv("AI_API_BASE_URL"), "Only the authorized endpoint is allowed");
            String credential = System.getenv("AI_API_KEY");
            assertTrue(credential != null && !credential.isBlank(), "Inject AI_API_KEY through the environment");
            config.setApiKey(credential);
            config.setApiBaseUrl("https://api.deepseek.com");
            config.setTimeout(Integer.parseInt(System.getenv().getOrDefault("AI_TIMEOUT", "30000")));
            config.setMaxTokens(Integer.parseInt(System.getenv().getOrDefault("AI_MAX_TOKENS", "2000")));
        } else {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat/completions", exchange -> {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                boolean judge = request.contains("独立的题目追问安全检查器");
                JsonNode sample = sampleRef.get();
                if (!judge) { generation.incrementAndGet(); }
                boolean retry = generation.get() > 1 && sample.has("mockRetryResponse");
                String answer = sample.path(judge ? retry ? "mockRetryJudgment" : "mockJudgment"
                        : retry ? "mockRetryResponse" : "mockResponse").asText();
                String data = "data: {\"model\":\"deepseek-flash\",\"choices\":[{\"index\":0,\"delta\":{\"content\":"
                        + json.writeValueAsString(answer) + "},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":20,\"total_tokens\":120}}\n\n"
                        + "data: [DONE]\n\n";
                byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            config.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setApiKey("simulation-placeholder");
        }
        try {
            var governance = mock(AiCallGovernanceService.class);
            var price = new AiCallTicket.Price(new BigDecimal("0.3"), new BigDecimal("1.2"));
            var calls = new ArrayList<Map<String, Object>>();
            when(governance.begin(any(), any())).thenAnswer(call -> {
                var request = (com.learnplatform.ai.model.ModelRequest) call.getArgument(1);
                var entry = new AiCallLog();
                entry.setPromptTemplate(request.messages().getFirst().content().contains("独立的") ? "judgment" : "generation");
                return new AiCallTicket(entry, Map.of("deepseek-flash", price));
            });
            doAnswer(call -> {
                AiCallTicket ticket = call.getArgument(0);
                ModelResult result = call.getArgument(1);
                var row = new LinkedHashMap<String, Object>();
                row.put("stage", ticket.entry().getPromptTemplate());
                row.put("rawOutput", result == null ? null : result.text());
                row.put("outcome", call.getArgument(2));
                row.put("latencyMs", call.getArgument(3));
                row.put("usage", result == null ? null : result.usage());
                row.put("costUsd", result == null ? null : AiCostCalculator.calculate(price, result.usage(), false));
                JsonNode current = sampleRef.get();
                boolean retry = generation.get() > 1 && current.has("mockRetryResponse");
                row.put("reviewUnsafe", !real && "generation".equals(ticket.entry().getPromptTemplate())
                        ? retry ? false : current.path("mockUnsafe").asBoolean() : null);
                calls.add(row);
                return null;
            }).when(governance).finish(any(), any(), anyString(), anyLong());
            var invocation = new AiInvocationService(new OpenAiProvider(config), governance);
            var answers = new QuestionAskAnswerService(invocation);
            JsonNode cases;
            try (var input = getClass().getResourceAsStream("/question-ask/cases.json")) { cases = json.readTree(input); }
            assertEquals(30, cases.size());
            var rows = new ArrayList<Map<String, Object>>();
            for (boolean thinking : List.of(true, false)) {
                config.setThinkingEnabled(thinking);
                for (JsonNode sample : cases) {
                    calls.clear();
                    generation.set(0);
                    sampleRef.set(sample);
                    boolean unlocked = sample.path("unlocked").asBoolean();
                    var visibleMaterials = new LinkedHashMap<String, Object>();
                    visibleMaterials.put("stem", "栈中元素的基本访问顺序是什么？");
                    visibleMaterials.put("options", List.of(Map.of("label", "A", "content", "后进先出"),
                            Map.of("label", "B", "content", "先进先出"), Map.of("label", "C", "content", "随机访问"),
                            Map.of("label", "D", "content", "按值排序")));
                    visibleMaterials.put("selected", "B");
                    visibleMaterials.put("selectedMisconception", "检查是否混淆了栈与队列的处理规则。");
                    visibleMaterials.put("hints", List.of("从操作发生的次序与移除的次序比较入手。"));
                    var full = new LinkedHashMap<>(visibleMaterials);
                    full.put("solution", "栈按后进先出的顺序处理元素。");
                    full.put("correctLabel", "A");
                    var materials = new QuestionAskMaterials.Snapshot(json.writeValueAsString(unlocked ? full : visibleMaterials),
                            json.writeValueAsString(full), unlocked, "A", "后进先出");
                    String visible = null;
                    String error = null;
                    long start = System.nanoTime();
                    try {
                        visible = answers.answer(new AiCallContext(1L, "question_ask", UUID.randomUUID()), materials,
                                sample.path("message").asText(), new Cancellation(), () -> { }, () -> { });
                    } catch (ModelException exception) { error = exception.code().name(); }
                    long duration = (System.nanoTime() - start) / 1_000_000;
                    boolean fallback = QuestionAskPolicy.FALLBACK.equals(visible);
                    boolean ruleLeak = !unlocked && visible != null && !QuestionAskPolicy.rulesPass(visible, "A", "后进先出");
                    if (!real) {
                        assertNull(error);
                        assertEquals(sample.path("expectMockRejection").asBoolean(), fallback, sample.path("id").asText());
                        assertFalse(ruleLeak);
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", sample.path("id").asText());
                    row.put("category", sample.path("category").asText());
                    row.put("message", sample.path("message").asText());
                    row.put("unlocked", unlocked);
                    row.put("thinking", thinking);
                    row.put("publicOutput", visible);
                    row.put("error", error);
                    row.put("fallback", fallback);
                    row.put("ruleLeak", ruleLeak);
                    row.put("reviewPublicLeak", real || unlocked || visible == null ? null
                            : !fallback && !sample.has("mockRetryResponse") && sample.path("mockUnsafe").asBoolean());
                    row.put("latencyMs", duration);
                    row.put("firstValidatedContentMs", visible == null ? null : duration);
                    row.put("calls", List.copyOf(calls));
                    row.put("totalTokens", calls.stream().map(c -> (ModelResult.Usage) c.get("usage"))
                            .filter(u -> u != null && u.totalTokens() != null).mapToInt(ModelResult.Usage::totalTokens).sum());
                    row.put("costUsd", calls.stream().map(c -> (BigDecimal) c.get("costUsd")).filter(c -> c != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    row.put("usageComplete", calls.stream().allMatch(c -> c.get("costUsd") != null));
                    rows.add(row);
                }
            }
            var report = new LinkedHashMap<String, Object>();
            report.put("responseOrigin", real ? "REAL_PROVIDER" : "SCRIPTED_HTTP_UPSTREAM");
            report.put("persistenceOrigin", "MOCK_GOVERNANCE_NO_DATABASE");
            report.put("materialOrigin", "SYNTHETIC_REFERENCE_NOT_DATABASE_REVIEW");
            report.put("teachingQuality", real ? "NOT_REVIEWED" : "NOT_EVALUATED");
            report.put("thinkingDecision", "PENDING_REAL_EVALUATION_AND_AI_REVIEW");
            report.put("metricNotes", "ruleLeak 仅规则扫描；语义泄露和判断误拦须由另一个模型（非本判断器）逐条标注 reviewPublicLeak、generation.reviewUnsafe 后计算，不能用同一判断器自证安全。成本与 tokens 为每次追问所有调用之和，usageComplete=false 时仅为已知小计。");
            report.put("cases", rows);
            Path path = Path.of("target/question-ask-evaluation/" + (real ? "real" : "mock") + ".json");
            Files.createDirectories(path.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), report);
            assertTrue(rows.stream().allMatch(row -> row.get("error") == null), "See evaluation report for upstream failures");
        } finally { if (server != null) { server.stop(0); } }
    }
}
