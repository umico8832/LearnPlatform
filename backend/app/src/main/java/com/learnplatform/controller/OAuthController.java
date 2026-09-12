package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.config.GoogleOAuthProperties;
import com.learnplatform.dto.LoginResponse;
import com.learnplatform.dto.OAuthProviderStatus;
import com.learnplatform.dto.OAuthTicketExchangeRequest;
import com.learnplatform.service.GoogleOAuthLoginService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/oauth")
public class OAuthController {
    private final GoogleOAuthProperties googleProperties;
    private final GoogleOAuthLoginService googleLoginService;

    public OAuthController(GoogleOAuthProperties googleProperties, GoogleOAuthLoginService googleLoginService) {
        this.googleProperties = googleProperties;
        this.googleLoginService = googleLoginService;
    }

    @GetMapping("/providers")
    public R<OAuthProviderStatus> providers() {
        return R.ok(new OAuthProviderStatus(googleProperties.isReady()));
    }

    @PostMapping("/exchange")
    public R<LoginResponse> exchange(@Valid @RequestBody OAuthTicketExchangeRequest request) {
        return R.ok(googleLoginService.exchangeTicket(request.getTicket()));
    }
}
