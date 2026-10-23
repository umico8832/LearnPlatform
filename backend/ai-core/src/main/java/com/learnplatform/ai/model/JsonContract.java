package com.learnplatform.ai.model;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

public final class JsonContract {
    private static final ObjectMapper JSON = JsonMapper.builderWithJackson2Defaults()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final SchemaRegistry SCHEMAS = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private JsonContract() { }

    public static JsonNode parse(String value) {
        try {
            JsonNode node = JSON.readTree(value);
            if (node == null) {
                throw new IllegalArgumentException("Empty JSON");
            }
            return node;
        } catch (Exception exception) {
            throw new ModelException(ModelException.Code.SCHEMA);
        }
    }

    public static void validateSchema(String schema) {
        JsonNode root = parse(schema);
        if (!root.isObject() || !"object".equals(root.path("type").asText())) {
            throw new IllegalArgumentException("A JSON object schema is required");
        }
        rejectExternalReferences(root);
        SCHEMAS.getSchema(root);
    }

    private static void rejectExternalReferences(JsonNode node) {
        if (node.isObject()) {
            for (String key : new String[]{"$ref", "$dynamicRef", "$schema", "$id"}) {
                if (node.has(key)) {
                    throw new IllegalArgumentException("Schema references and external dialects are not supported");
                }
            }
        }
        node.values().forEach(JsonContract::rejectExternalReferences);
    }

    public static void validate(String content, String schema) {
        validateSchema(schema);
        if (!SCHEMAS.getSchema(parse(schema)).validate(parse(content)).isEmpty()) {
            throw new ModelException(ModelException.Code.SCHEMA);
        }
    }
}
