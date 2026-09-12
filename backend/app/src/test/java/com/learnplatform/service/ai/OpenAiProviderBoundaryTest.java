package com.learnplatform.service.ai;

import com.learnplatform.ai.model.ModelException;
import com.learnplatform.config.AiConfig;
import com.learnplatform.support.TestCloudServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiProviderBoundaryTest {
    @Test void rejectsStreamThatEndsWithoutCompletion() throws Exception {
        try (var server = new TestCloudServer()) {
            server.contentType = "text/event-stream";
            server.response = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"partial\"}}]}\n\n";
            assertThrows(ModelException.class, () -> provider(server).chatStream("system", "user", chunk -> { }));
        }
    }

    @Test void rejectsTruncatedTextInsteadOfTreatingItAsSuccess() throws Exception {
        try (var server = new TestCloudServer()) {
            server.response = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"partial\"},"
                    + "\"finish_reason\":\"length\"}]}";
            assertEquals(ModelException.Code.TRUNCATED,
                    assertThrows(ModelException.class, () -> provider(server).chat("system", "user")).code());
        }
    }

    private OpenAiProvider provider(TestCloudServer server) {
        var config = new AiConfig();
        config.setEnabled(true);
        config.setApiKey("test-placeholder");
        config.setApiBaseUrl(server.url());
        return new OpenAiProvider(config);
    }
}
