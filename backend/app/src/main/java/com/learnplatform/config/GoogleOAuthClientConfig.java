package com.learnplatform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
@ConditionalOnProperty(prefix = "app.oauth.google", name = "enabled", havingValue = "true")
public class GoogleOAuthClientConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(GoogleOAuthProperties properties) {
        if (!properties.isReady()) {
            throw new IllegalStateException(
                    "Google OAuth 已启用，但客户端 ID、密钥或回调地址未完整配置");
        }
        ClientRegistration registration = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .scope("openid", "profile", "email")
                .redirectUri(properties.getRedirectUri())
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }
}
