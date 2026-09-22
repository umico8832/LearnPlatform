package com.learnplatform.ai.model;

import java.util.List;

public record EmbeddingRequest(String model, List<String> inputs, Integer dimensions) {
    public EmbeddingRequest {
        if (model == null || model.isBlank() || inputs == null || inputs.isEmpty() || inputs.size() > 2048
                || inputs.stream().anyMatch(value -> value == null || value.isBlank())
                || dimensions != null && dimensions <= 0) {
            throw new IllegalArgumentException("Invalid embedding request");
        }
        inputs = List.copyOf(inputs);
    }

    @Override public String toString() {
        return "EmbeddingRequest[model=" + model + ", inputCount=" + inputs.size() + ", dimensions=" + dimensions + "]";
    }
}
