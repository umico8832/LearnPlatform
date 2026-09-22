package com.learnplatform.service.ai;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.ai.model.EmbeddingResult;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.provider.CloudEmbeddingModel;
import com.learnplatform.config.AiConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Component
public class OpenAiEmbeddingProvider implements EmbeddingProvider {
    private final AiConfig config;

    public OpenAiEmbeddingProvider(AiConfig config) {
        this.config = config;
    }

    @Override
    public void validate(EmbeddingRequest request) {
        AiConfig.EmbeddingConfig embedding = config.getEmbedding();
        if (!embedding.isEnabled()) {
            throw new ModelException(ModelException.Code.CONFIGURATION);
        }
        if (!Objects.equals(embedding.getModel(), request.model())) {
            throw new ModelException(ModelException.Code.UNSUPPORTED);
        }
        if (embedding.getDimensions() != null && !Objects.equals(embedding.getDimensions(), request.dimensions())) {
            throw new ModelException(ModelException.Code.UNSUPPORTED);
        }
        model(embedding).validate(request);
    }

    @Override
    public EmbeddingResult embed(EmbeddingRequest request, Cancellation cancellation) {
        validate(request);
        return model(config.getEmbedding()).embed(request, cancellation);
    }

    private CloudEmbeddingModel model(AiConfig.EmbeddingConfig embedding) {
        if (embedding.getTimeoutSeconds() <= 0) {
            throw new ModelException(ModelException.Code.CONFIGURATION);
        }
        return new CloudEmbeddingModel(embedding.getApiBaseUrl(), embedding.getApiKey(),
                Duration.ofSeconds(embedding.getTimeoutSeconds()));
    }
}
