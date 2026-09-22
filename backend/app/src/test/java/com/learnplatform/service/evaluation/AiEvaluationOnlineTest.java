package com.learnplatform.service.evaluation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.learnplatform.config.AiConfig;
import com.learnplatform.service.ai.OpenAiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "AI_EVAL_ONLINE", matches = "true")
class AiEvaluationOnlineTest {
    @Test
    void evaluateConfiguredProvider() throws Exception {
        AiConfig config = configuration(new LinkedHashMap<>(System.getenv()));
        var corpus = AiEvaluationCorpus.load();
        Set<String> selected = Arrays.stream(System.getenv().getOrDefault("AI_EVAL_CASES", "").split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toSet());
        Set<String> eligible = corpus.cases().stream().filter(AiEvaluationCorpus.Case::online)
                .map(AiEvaluationCorpus.Case::id).collect(Collectors.toSet());
        assertTrue(eligible.containsAll(selected), "AI_EVAL_CASES must contain eligible corpus IDs");
        boolean evaluatesAgent = corpus.cases().stream().anyMatch(sample -> sample.online()
                && "AGENT".equals(sample.route()) && (selected.isEmpty() || selected.contains(sample.id())));
        assertTrue(!evaluatesAgent || config.isToolsSupported(),
                "Set AI_TOOLS_SUPPORTED=true when evaluating Tutor Agent cases");
        List<Map<String, Object>> results = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level previous = root.getLevel();
        try {
            // Provider exceptions can contain upstream response bodies or endpoint details.
            root.setLevel(Level.OFF);
            var provider = new OpenAiProvider(config);
            for (var sample : corpus.cases()) {
                if (!sample.online() || (!selected.isEmpty() && !selected.contains(sample.id()))) {
                    results.add(Map.of("caseId", sample.id(), "contractStatus", "SKIPPED",
                            "reason", sample.online() ? "NOT_SELECTED" : "OFFLINE_FAULT_OR_PERMISSION_CASE",
                            "teachingQuality", "NOT_EVALUATED"));
                    continue;
                }
                var fixture = new AiEvaluationFixture(corpus, sample, config, provider);
                fixture.execute();
                List<String> checks = fixture.check(true);
                results.add(AiEvaluationReport.result(sample, fixture, config, true, checks));
                if (!checks.isEmpty()) { failures.add(sample.id() + ": " + checks); }
            }
        } finally {
            root.setLevel(previous);
            AiEvaluationReport.write(corpus, config, true, results);
        }
        assertTrue(failures.isEmpty(), () -> "Online contract failures (quality still needs review): " + failures);
    }

    static AiConfig configuration(Map<String, Object> environment) {
        Binder binder = new Binder(ConfigurationPropertySources.from(
                new SystemEnvironmentPropertySource("evaluationEnvironment", environment)));
        AiConfig config = binder.bind("ai", Bindable.of(AiConfig.class)).orElseGet(AiConfig::new);
        assertTrue(config.isEnabled(), "Set AI_ENABLED=true explicitly");
        assertTrue(config.getApiKey() != null && !config.getApiKey().isBlank(), "AI_API_KEY is required");
        assertFalse(environment.getOrDefault("AI_MODEL", "").toString().isBlank(), "AI_MODEL is required");
        assertTrue(config.getTimeout() > 0 && config.getMaxTokens() > 0, "Invalid model limits");
        URI endpoint;
        try {
            endpoint = URI.create(config.getApiBaseUrl());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid AI_API_BASE_URL");
        }
        assertTrue(Set.of("https", "http").contains(endpoint.getScheme()) && endpoint.getHost() != null
                && endpoint.getUserInfo() == null && endpoint.getQuery() == null && endpoint.getFragment() == null,
                "AI_API_BASE_URL must be an HTTP(S) endpoint without credentials or query parameters");
        return config;
    }
}
