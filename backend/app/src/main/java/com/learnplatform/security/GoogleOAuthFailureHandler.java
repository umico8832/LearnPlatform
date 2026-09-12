package com.learnplatform.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class GoogleOAuthFailureHandler implements AuthenticationFailureHandler {
    private final String callbackUrl;

    public GoogleOAuthFailureHandler(@Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.callbackUrl = frontendUrl.replaceAll("/+$", "") + "/oauth/callback";
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String errorCode = "oauth_failed";
        if (exception instanceof OAuth2AuthenticationException oauthException
                && "access_denied".equals(oauthException.getError().getErrorCode())) {
            errorCode = "access_denied";
        }
        response.setHeader("Cache-Control", "no-store");
        String target = UriComponentsBuilder.fromUriString(callbackUrl)
                .queryParam("error", errorCode)
                .build()
                .encode()
                .toUriString();
        response.sendRedirect(target);
    }
}
