package com.learnplatform.service.ai;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.config.AiConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiProviderTest {
    @Test void disabledConfigurationCannotReachCloud() {
        var provider = new OpenAiProvider(new AiConfig());
        var request = ModelRequest.text("system", "user", provider.defaultOptions());
        assertEquals(ModelException.Code.CONFIGURATION, assertThrows(ModelException.class,
                () -> provider.complete(request, new Cancellation())).code());
    }
    @Test void modelOverrideRequiresAnExplicitlyConfiguredProvider() {
        var config = new AiConfig();
        config.setEnabled(true);
        config.setApiKey("test-placeholder");
        var provider = new OpenAiProvider(config);
        var request = ModelRequest.text("system", "user", new ModelRequest.Options("unconfigured", 20, null));
        assertEquals(ModelException.Code.UNSUPPORTED, assertThrows(ModelException.class,
                () -> provider.complete(request, new Cancellation())).code());
    }

    @Test void embeddingRequiresItsOwnEnabledConfigurationAndModel() {
        var config = new AiConfig();
        var provider = new OpenAiEmbeddingProvider(config);
        var request = new EmbeddingRequest("embedding", java.util.List.of("chunk"), null);
        assertEquals(ModelException.Code.CONFIGURATION, assertThrows(ModelException.class,
                () -> provider.embed(request, new Cancellation())).code());

        config.getEmbedding().setEnabled(true);
        config.getEmbedding().setApiKey("test-placeholder");
        config.getEmbedding().setApiBaseUrl("https://example.test/v1");
        config.getEmbedding().setModel("configured");
        assertEquals(ModelException.Code.UNSUPPORTED, assertThrows(ModelException.class,
                () -> provider.embed(request, new Cancellation())).code());
    }

    @Test void embeddingRejectsNonPositiveTimeoutAsConfiguration() {
        var config = new AiConfig();
        config.getEmbedding().setEnabled(true);
        config.getEmbedding().setApiKey("test-placeholder");
        config.getEmbedding().setApiBaseUrl("https://example.test/v1");
        config.getEmbedding().setModel("embedding");
        config.getEmbedding().setTimeoutSeconds(0);

        assertEquals(ModelException.Code.CONFIGURATION, assertThrows(ModelException.class,
                () -> new OpenAiEmbeddingProvider(config).embed(
                        new EmbeddingRequest("embedding", java.util.List.of("chunk"), null), new Cancellation())).code());
    }

    @Test void embeddingRejectsDimensionsThatDifferFromConfiguredIndexSpace() {
        var config = new AiConfig();
        config.getEmbedding().setEnabled(true);
        config.getEmbedding().setApiKey("test-placeholder");
        config.getEmbedding().setApiBaseUrl("https://example.test/v1");
        config.getEmbedding().setModel("embedding");
        config.getEmbedding().setDimensions(3);

        assertEquals(ModelException.Code.UNSUPPORTED, assertThrows(ModelException.class,
                () -> new OpenAiEmbeddingProvider(config).embed(
                        new EmbeddingRequest("embedding", java.util.List.of("chunk"), 4), new Cancellation())).code());
    }
}
