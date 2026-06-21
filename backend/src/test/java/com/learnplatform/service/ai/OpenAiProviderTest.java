package com.learnplatform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

class OpenAiProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesContentDeltaFromSseLine() {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}";

        assertEquals("你好", OpenAiProvider.parseStreamContent(line, objectMapper));
    }

    @Test
    void ignoresDoneAndNonContentEvents() {
        assertNull(OpenAiProvider.parseStreamContent("data: [DONE]", objectMapper));
        assertNull(OpenAiProvider.parseStreamContent("event: ping", objectMapper));
        assertNull(OpenAiProvider.parseStreamContent(
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}", objectMapper));
    }

    @Test
    void rejectsMalformedSseJson() {
        assertThrows(BusinessException.class,
                () -> OpenAiProvider.parseStreamContent("data: {invalid", objectMapper));
    }

    @Test
    void parsesUsageFromCompletionResponse() {
        AiTokenUsage usage = OpenAiProvider.parseTokenUsage(Map.of("usage", Map.of(
                "prompt_tokens", 12, "completion_tokens", 8, "total_tokens", 20)));

        assertNotNull(usage);
        assertEquals(12, usage.promptTokens());
        assertEquals(8, usage.completionTokens());
        assertEquals(20, usage.totalTokens());
    }

    @Test
    void parsesUsageFromFinalStreamEvent() throws Exception {
        AiTokenUsage usage = OpenAiProvider.parseTokenUsage(objectMapper.readTree(
                "{\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8,\"total_tokens\":20}}"));

        assertNotNull(usage);
        assertEquals(20, usage.totalTokens());
    }
}
