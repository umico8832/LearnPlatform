package com.learnplatform.service.ai;

import com.learnplatform.ai.model.Cancellation;
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
}
