package com.learnplatform.ai.model;

import java.util.List;

public record EmbeddingResult(List<List<Double>> vectors, String model, ModelResult.Usage usage) {
    public EmbeddingResult {
        vectors = vectors.stream().map(List::copyOf).toList();
    }

    public ModelResult auditResult() {
        return new ModelResult(null, List.of(), model, null, null, usage);
    }

    @Override public String toString() {
        return "EmbeddingResult[model=" + model + ", vectorCount=" + vectors.size() + ", usage=" + usage + "]";
    }
}
