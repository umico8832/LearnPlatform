package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.LoginResponse;
import com.learnplatform.dto.RegisterRequest;
import com.learnplatform.security.JwtTokenProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("integration")
@Transactional
@Tag("integration")
class AuthRegistrationIntegrationTest extends IntegrationTestBase {
    @Autowired private AuthService authService;
    @Autowired private AuthTokenHasher tokenHasher;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void verifiedRegistrationReturnsValidUserSessionAndConsumesTicket() {
        String email = "registration-session@example.com";
        String ticket = "registration-ticket-placeholder";
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO email_verification
                    (email, purpose, code_hash, ticket_hash, attempt_count, expires_at, verified_at, create_time)
                VALUES (?, 'register', ?, ?, 0, ?, ?, ?)
                """, email, tokenHasher.hash("unused-code"), tokenHasher.hash(ticket),
                now.plusMinutes(5), now, now);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("registration-session");
        request.setEmail(email);
        request.setPassword("test-password-placeholder");
        request.setVerificationTicket(ticket);
        LoginResponse response = authService.register(request);

        assertNotNull(response.getUser().getId());
        assertEquals("USER", response.getUser().getRole());
        assertTrue(jwtTokenProvider.validateToken(response.getToken()));
        assertEquals(response.getUser().getId(), jwtTokenProvider.getUserIdFromToken(response.getToken()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM email_verification
                WHERE email = ? AND used_at IS NOT NULL
                """, Integer.class, email));
    }

    @Test
    void invalidTicketCannotCreateUserOrSession() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("registration-no-ticket");
        request.setEmail("registration-no-ticket@example.com");
        request.setPassword("test-password-placeholder");
        request.setVerificationTicket("invalid-ticket-placeholder");

        assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE username = ?", Integer.class,
                request.getUsername()));
    }
}
