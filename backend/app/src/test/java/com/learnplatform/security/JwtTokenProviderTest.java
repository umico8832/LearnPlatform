package com.learnplatform.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
                "test-secret-key-that-is-long-enough-for-hs256");
        ReflectionTestUtils.setField(tokenProvider, "jwtExpiration", 3600L);
    }

    @Test
    void generatesAndParsesTokenClaims() {
        String token = tokenProvider.generateToken(42L, "learner", "USER");

        assertTrue(tokenProvider.validateToken(token));
        assertEquals(42L, tokenProvider.getUserIdFromToken(token));
        assertEquals("learner", tokenProvider.getUsernameFromToken(token));
        assertEquals("USER", tokenProvider.getRoleFromToken(token));
    }

    @Test
    void rejectsMalformedToken() {
        assertFalse(tokenProvider.validateToken("not-a-jwt"));
    }
}
