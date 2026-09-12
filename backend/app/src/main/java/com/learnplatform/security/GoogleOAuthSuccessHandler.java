package com.learnplatform.security;

import com.learnplatform.common.exception.OAuthLoginException;
import com.learnplatform.service.GoogleOAuthLoginService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthSuccessHandler.class);

    private final GoogleOAuthLoginService loginService;
    private final String callbackUrl;

    public GoogleOAuthSuccessHandler(GoogleOAuthLoginService loginService,
                                     @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.loginService = loginService;
        this.callbackUrl = frontendUrl.replaceAll("/+$", "") + "/oauth/callback";
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            redirectError(response, "invalid_identity");
            return;
        }
        try {
            String ticket = loginService.completeLogin(oidcUser);
            response.sendRedirect(callbackUrl + "#ticket=" + ticket);
        } catch (OAuthLoginException e) {
            log.info("Google 登录未完成: code={}", e.getErrorCode());
            redirectError(response, e.getErrorCode());
        } catch (RuntimeException e) {
            log.error("Google 登录处理失败", e);
            redirectError(response, "oauth_failed");
        }
    }

    private void redirectError(HttpServletResponse response, String errorCode) throws IOException {
        String target = UriComponentsBuilder.fromUriString(callbackUrl)
                .queryParam("error", errorCode)
                .build()
                .encode()
                .toUriString();
        response.sendRedirect(target);
    }
}
