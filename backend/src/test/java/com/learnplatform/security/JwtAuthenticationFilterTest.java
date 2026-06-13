package com.learnplatform.security;

import com.learnplatform.entity.User;
import com.learnplatform.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private UserMapper userMapper;
    @Mock private FilterChain filterChain;
    private JwtTokenProvider tokenProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
                "test-secret-key-that-is-long-enough-for-hs256");
        ReflectionTestUtils.setField(tokenProvider, "jwtExpiration", 3600L);
        filter = new JwtAuthenticationFilter(tokenProvider, userMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesCurrentEnabledUser() throws Exception {
        String token = tokenProvider.generateToken(7L, "learner", "USER");
        when(userMapper.selectById(7L)).thenReturn(user("learner", "USER", 1,
                LocalDateTime.now().minusMinutes(1)));

        filter.doFilter(request(token), new MockHttpServletResponse(), filterChain);

        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
    }

    @Test
    void rejectsDisabledOrChangedUser() throws Exception {
        String token = tokenProvider.generateToken(7L, "learner", "USER");
        when(userMapper.selectById(7L)).thenReturn(user("learner", "USER", 0,
                LocalDateTime.now().minusMinutes(1)));

        filter.doFilter(request(token), new MockHttpServletResponse(), filterChain);

        assertFalse(SecurityContextHolder.getContext().getAuthentication() != null);
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private User user(String username, String role, int status, LocalDateTime updateTime) {
        User user = new User();
        user.setId(7L);
        user.setUsername(username);
        user.setRole(role);
        user.setStatus(status);
        user.setUpdateTime(updateTime);
        return user;
    }
}
