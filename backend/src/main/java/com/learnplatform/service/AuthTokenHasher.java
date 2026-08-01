package com.learnplatform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class AuthTokenHasher {
    private final byte[] secret;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthTokenHasher(@Value("${auth.token-secret:}") String authSecret,
                           @Value("${jwt.secret}") String jwtSecret) {
        String effectiveSecret = authSecret == null || authSecret.isBlank() ? jwtSecret : authSecret;
        this.secret = effectiveSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法计算认证令牌摘要", e);
        }
    }

    public String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String randomCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
