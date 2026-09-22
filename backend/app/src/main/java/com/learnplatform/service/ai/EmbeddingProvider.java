package com.learnplatform.service.ai;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.ai.model.EmbeddingResult;

public interface EmbeddingProvider {
    void validate(EmbeddingRequest request);
    EmbeddingResult embed(EmbeddingRequest request, Cancellation cancellation);
}
