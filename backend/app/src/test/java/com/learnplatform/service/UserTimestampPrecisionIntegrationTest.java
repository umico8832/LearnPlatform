package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.time.BusinessTime;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.security.JwtAuthenticationFilter;
import com.learnplatform.security.JwtTokenProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class UserTimestampPrecisionIntegrationTest extends IntegrationTestBase {
    @Autowired UserMapper users;

    @Test void userCreatedLateInASecondAuthenticatesWithoutWaitingOrWeakeningRevocation() throws Exception {
        LocalDateTime created = LocalDateTime.parse("2026-07-01T00:00:00.900");
        User user = new User();
        user.setUsername("timestamp-precision");
        user.setPassword("test-only-password-placeholder");
        user.setRole("USER");
        user.setStatus(1);
        user.setCreateTime(created);
        user.setUpdateTime(created);
        users.insert(user);

        JwtTokenProvider tokens = mock(JwtTokenProvider.class);
        when(tokens.validateToken("test-token")).thenReturn(true);
        when(tokens.getUserIdFromToken("test-token")).thenReturn(user.getId());
        when(tokens.getUsernameFromToken("test-token")).thenReturn(user.getUsername());
        when(tokens.getRoleFromToken("test-token")).thenReturn("USER");
        when(tokens.getAuthVersionFromToken("test-token")).thenReturn(0);
        when(tokens.getIssuedAtFromToken("test-token")).thenReturn(
                Date.from(created.withNano(0).atZone(BusinessTime.ZONE).toInstant()));
        var filter = new JwtAuthenticationFilter(tokens, users);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        try {
            SecurityContextHolder.clearContext();
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            SecurityContextHolder.clearContext();

            users.resetPasswordAndRevokeTokens(user.getId(), "test-only-new-password-placeholder");
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
