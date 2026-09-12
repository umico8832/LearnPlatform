package com.learnplatform.service.ai;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelEvent;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.ai.provider.CloudChatModel;
import com.learnplatform.config.AiConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Consumer;

@Component
public class OpenAiProvider implements AiProvider {
    private final AiConfig config;

    public OpenAiProvider(AiConfig config) {
        this.config = config;
    }

    @Override public ModelRequest.Options defaultOptions() {
        return new ModelRequest.Options(config.getModel(), config.getMaxTokens(), config.getTemperature());
    }

    @Override public void validate(ModelRequest request) {
        if (!config.isEnabled()) {
            throw new ModelException(ModelException.Code.CONFIGURATION);
        }
        if (!config.getModel().equals(request.options().model())) {
            throw new ModelException(ModelException.Code.UNSUPPORTED);
        }
        model().validate(request);
    }

    @Override public ModelResult complete(ModelRequest request, Cancellation cancellation) {
        validate(request);
        return model().complete(request, cancellation);
    }

    @Override public ModelResult stream(ModelRequest request, Consumer<ModelEvent> events, Cancellation cancellation) {
        validate(request);
        return model().stream(request, events, cancellation);
    }

    private CloudChatModel model() {
        return new CloudChatModel(config.getApiBaseUrl(), config.getApiKey(), Duration.ofMillis(config.getTimeout()),
                config.isStreamIncludeUsage(), config.isToolsSupported(), config.isStructuredOutputSupported());
    }
}
