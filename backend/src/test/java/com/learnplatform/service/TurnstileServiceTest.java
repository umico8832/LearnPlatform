package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TurnstileServiceTest {
    @Test
    void disabledVerifierDoesNotCallUpstream() {
        TurnstileService service = new TurnstileService(false, "", new RestTemplate());
        assertDoesNotThrow(() -> service.verify("", "127.0.0.1"));
    }

    @Test
    void enabledVerifierRejectsMissingToken() {
        TurnstileService service = new TurnstileService(true, "secret", new RestTemplate());
        assertThrows(BusinessException.class, () -> service.verify("", "127.0.0.1"));
    }

    @Test
    void acceptsSuccessfulCloudflareResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        TurnstileService service = new TurnstileService(true, "secret", restTemplate);
        assertDoesNotThrow(() -> service.verify("token", "127.0.0.1"));
        server.verify();
    }

    @Test
    void rejectsFailedCloudflareResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
                .andRespond(withSuccess(
                        "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}",
                        MediaType.APPLICATION_JSON));
        TurnstileService service = new TurnstileService(true, "secret", restTemplate);
        assertThrows(BusinessException.class, () -> service.verify("bad-token", "127.0.0.1"));
        server.verify();
    }
}
