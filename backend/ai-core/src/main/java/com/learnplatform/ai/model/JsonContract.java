package com.learnplatform.ai.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

public final class JsonContract {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final JsonSchemaFactory SCHEMAS = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

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
        node.elements().forEachRemaining(JsonContract::rejectExternalReferences);
    }

    public static void validate(String content, String schema) {
        validateSchema(schema);
        if (!SCHEMAS.getSchema(parse(schema)).validate(parse(content)).isEmpty()) {
            throw new ModelException(ModelException.Code.SCHEMA);
        }
    }
}
