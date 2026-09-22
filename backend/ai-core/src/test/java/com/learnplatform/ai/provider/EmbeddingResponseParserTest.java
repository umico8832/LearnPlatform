package com.learnplatform.ai.provider;

import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.ai.model.ModelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingResponseParserTest {
    private final EmbeddingRequest request = new EmbeddingRequest("requested", List.of("a", "b"), 2);

    @Test void restoresInputOrderAndPreservesExactUsage() {
        var result = EmbeddingResponseParser.parse(response("""
                [{"index":1,"embedding":[0.3,0.4]},{"index":0,"embedding":[0.1,0.2]}]
                """, "{\"prompt_tokens\":7}"), request);
        assertEquals(List.of(List.of(0.1, 0.2), List.of(0.3, 0.4)), result.vectors());
        assertEquals("actual", result.model());
        assertEquals(7, result.usage().inputTokens());
        assertNull(result.usage().outputTokens());
        assertNull(result.usage().totalTokens());
        assertNull(result.auditResult().text());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "{}",
        "[{\"index\":0,\"embedding\":[1,2]}]",
        "[{\"index\":0,\"embedding\":[1,2]},{\"index\":0,\"embedding\":[3,4]}]",
        "[{\"index\":-1,\"embedding\":[1,2]},{\"index\":1,\"embedding\":[3,4]}]",
        "[{\"index\":2,\"embedding\":[1,2]},{\"index\":1,\"embedding\":[3,4]}]",
        "[{\"index\":0.5,\"embedding\":[1,2]},{\"index\":1,\"embedding\":[3,4]}]",
        "[{\"embedding\":[1,2]},{\"index\":1,\"embedding\":[3,4]}]",
        "[{\"index\":0,\"embedding\":[1]},{\"index\":1,\"embedding\":[3,4]}]",
        "[{\"index\":0,\"embedding\":[\"1\",2]},{\"index\":1,\"embedding\":[3,4]}]",
        "[{\"index\":0,\"embedding\":[1e400,2]},{\"index\":1,\"embedding\":[3,4]}]"})
    void rejectsMalformedVectorsAndPreservesBilledUsage(String data) {
        var error = assertThrows(ModelException.class,
                () -> EmbeddingResponseParser.parse(response(data, "{\"prompt_tokens\":7,\"total_tokens\":7}"), request));
        assertEquals(ModelException.Code.PROTOCOL, error.code());
        assertEquals(7, error.partialResult().usage().inputTokens());
        assertEquals("actual", error.partialResult().model());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"prompt_tokens\":-1}", "{\"prompt_tokens\":1.5}",
            "{\"total_tokens\":2147483648}", "{\"prompt_tokens\":\"7\"}", "[]"})
    void rejectsInvalidUsageWithoutInventingTokenCounts(String usage) {
        assertEquals(ModelException.Code.PROTOCOL, assertThrows(ModelException.class,
                () -> EmbeddingResponseParser.parse(response("[]", usage), request)).code());
    }

    @Test void rejectsInconsistentDimensionsEvenWithoutRequestedDimensions() {
        var automatic = new EmbeddingRequest("requested", List.of("a", "b"), null);
        assertThrows(ModelException.class, () -> EmbeddingResponseParser.parse(response(
                "[{\"index\":0,\"embedding\":[1]},{\"index\":1,\"embedding\":[2,3]}]", "null"), automatic));
    }

    @Test void acceptsMissingUsageAndMakesVectorsImmutable() {
        var result = EmbeddingResponseParser.parse(response(
                "[{\"index\":0,\"embedding\":[1,2]},{\"index\":1,\"embedding\":[3,4]}]", "null"), request);
        assertNull(result.usage());
        assertThrows(UnsupportedOperationException.class, () -> result.vectors().getFirst().add(1.0));
    }

    private String response(String data, String usage) {
        return "{\"model\":\"actual\",\"data\":" + data + ",\"usage\":" + usage + "}";
    }
}
