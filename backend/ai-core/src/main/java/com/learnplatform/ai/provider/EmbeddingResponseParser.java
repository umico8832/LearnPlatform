package com.learnplatform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.ai.model.EmbeddingResult;
import com.learnplatform.ai.model.JsonContract;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class EmbeddingResponseParser {
    private EmbeddingResponseParser() { }

    static EmbeddingResult parse(String body, EmbeddingRequest request) {
        ModelResult metadata = null;
        try {
            var root = JsonContract.parse(body);
            if (!root.isObject()) {
                throw new IllegalArgumentException();
            }
            String model = optionalText(root.path("model"));
            var usage = root.path("usage");
            metadata = new ModelResult(null, List.of(), model, null, null, null);
            if (!usage.isMissingNode() && !usage.isNull()) {
                if (!usage.isObject()) {
                    throw new IllegalArgumentException();
                }
                metadata = new ModelResult(null, List.of(), model, null, null,
                        new ModelResult.Usage(optionalCount(usage.path("prompt_tokens")), null,
                                optionalCount(usage.path("total_tokens"))));
            }
            var data = root.path("data");
            if (root.hasNonNull("error") || !data.isArray() || data.size() != request.inputs().size()) {
                throw new IllegalArgumentException();
            }
            var vectors = new ArrayList<List<Double>>(Collections.nCopies(data.size(), null));
            Integer dimensions = request.dimensions();
            for (var item : data) {
                Integer index = optionalCount(item.path("index"));
                if (index == null || index >= vectors.size() || vectors.get(index) != null) {
                    throw new IllegalArgumentException();
                }
                var values = item.path("embedding");
                if (!values.isArray() || values.isEmpty()) {
                    throw new IllegalArgumentException();
                }
                if (dimensions == null) {
                    dimensions = values.size();
                }
                if (values.size() != dimensions) {
                    throw new IllegalArgumentException();
                }
                var vector = new ArrayList<Double>();
                for (var value : values) {
                    if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
                        throw new IllegalArgumentException();
                    }
                    vector.add(value.doubleValue());
                }
                vectors.set(index, vector);
            }
            return new EmbeddingResult(vectors, model, metadata.usage());
        } catch (RuntimeException exception) {
            throw new ModelException(ModelException.Code.PROTOCOL, metadata);
        }
    }

    private static String optionalText(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException();
        }
        return value.textValue();
    }

    private static Integer optionalCount(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw new IllegalArgumentException();
        }
        return value.intValue();
    }
}
