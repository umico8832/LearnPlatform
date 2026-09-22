package com.learnplatform.service.evaluation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.config.AiConfig;
import com.learnplatform.service.ai.OpenAiEmbeddingProvider;
import com.learnplatform.service.knowledge.vector.QdrantKnowledgeVectorStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "AI_RAG_EVAL_ONLINE", matches = "true")
class KnowledgeRetrievalEvaluationOnlineTest {
    @Test void evaluateRealEmbeddingInIsolatedVectorStore() throws Exception {
        AiConfig config = configuration(new LinkedHashMap<>(System.getenv()));
        var corpus = KnowledgeRetrievalEvaluation.load();
        try (var container = new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.16.0"))
                .withExposedPorts(6333)) {
            container.start();
            try (var store = new QdrantKnowledgeVectorStore(
                    "http://" + container.getHost() + ":" + container.getMappedPort(6333), null,
                    "evaluation_" + UUID.randomUUID(), Duration.ofSeconds(30))) {
                Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
                Level previous = root.getLevel();
                Map<String, Object> report;
                try {
                    root.setLevel(Level.OFF);
                    report = KnowledgeRetrievalEvaluation.run(corpus, config, new OpenAiEmbeddingProvider(config), store, true);
                } finally {
                    root.setLevel(previous);
                }
                Path output = Path.of("target", "ai-evaluation", "retrieval-online.json");
                Files.createDirectories(output.getParent());
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
                assertEquals("SUCCEEDED", report.get("executionStatus"),
                        "Retrieval evaluation failed; see sanitized retrieval-online.json");
            }
        }
    }

    static AiConfig configuration(Map<String, Object> environment) {
        var binder = new Binder(ConfigurationPropertySources.from(
                new SystemEnvironmentPropertySource("systemEnvironment", environment)));
        AiConfig config = binder.bind("ai", Bindable.of(AiConfig.class)).orElseGet(AiConfig::new);
        var embedding = config.getEmbedding();
        assertTrue(embedding.isEnabled(), "Set AI_EMBEDDING_ENABLED=true explicitly");
        assertTrue(embedding.getApiKey() != null && !embedding.getApiKey().isBlank(), "AI_EMBEDDING_API_KEY is required");
        assertTrue(embedding.getModel() != null && !embedding.getModel().isBlank(), "AI_EMBEDDING_MODEL is required");
        assertTrue(embedding.getDimensions() != null && embedding.getDimensions() > 0
                && embedding.getDimensions() <= 65536, "AI_EMBEDDING_DIMENSIONS must be between 1 and 65536");
        assertTrue(embedding.getTimeoutSeconds() > 0 && embedding.getTimeoutSeconds() <= 60,
                "AI_EMBEDDING_TIMEOUT_SECONDS must be between 1 and 60");
        URI endpoint;
        try {
            endpoint = URI.create(embedding.getApiBaseUrl());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid AI_EMBEDDING_API_BASE_URL");
        }
        assertTrue(endpoint.getScheme() != null && Set.of("https", "http").contains(endpoint.getScheme())
                && endpoint.getHost() != null
                && endpoint.getUserInfo() == null && endpoint.getQuery() == null && endpoint.getFragment() == null,
                "AI_EMBEDDING_API_BASE_URL must be HTTP(S), without credentials or query parameters");
        Object configuredPrice = environment.get("AI_RAG_EVAL_INPUT_PRICE_PER_MILLION");
        if (configuredPrice != null) {
            BigDecimal inputPrice;
            try {
                inputPrice = new BigDecimal(configuredPrice.toString());
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Invalid AI_RAG_EVAL_INPUT_PRICE_PER_MILLION");
            }
            assertTrue(inputPrice.signum() >= 0, "Embedding input price must be nonnegative");
            var price = new AiConfig.ModelPrice();
            price.setInputPerMillion(inputPrice);
            config.getModelPrices().put(embedding.getModel(), price);
        }
        return config;
    }
}
