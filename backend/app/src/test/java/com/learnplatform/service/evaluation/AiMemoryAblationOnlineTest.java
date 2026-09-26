package com.learnplatform.service.evaluation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.learnplatform.service.ai.OpenAiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "AI_MEMORY_EVAL_ONLINE", matches = "true")
class AiMemoryAblationOnlineTest {
    @Test
    void compareConfiguredProviderWithSyntheticMemory() throws Exception {
        var config = AiEvaluationOnlineTest.configuration(new LinkedHashMap<>(System.getenv()));
        assertTrue(config.isToolsSupported(), "Set AI_TOOLS_SUPPORTED=true for memory ablation");
        var corpus = AiMemoryAblationCorpus.load();
        var settings = AiMemoryAblationEvaluation.Settings.from(System.getenv(), corpus);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level previous = root.getLevel();
        try {
            // Upstream errors may contain response bodies or credentials; reports retain only safe failure types.
            root.setLevel(Level.OFF);
            var report = AiMemoryAblationEvaluation.run(corpus, config, new OpenAiProvider(config), settings);
            Path output = AiMemoryAblationEvaluation.write(report, Path.of("target", "ai-evaluation", "memory-ablation"));
            assertEquals("PASS", report.get("contractStatus"), "Review failed contracts in " + output);
        } finally {
            root.setLevel(previous);
        }
    }
}
