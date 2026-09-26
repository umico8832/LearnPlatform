package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.security.JwtTokenProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Transactional
@Tag("integration")
class AdminPasswordResetIntegrationTest extends IntegrationTestBase {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserMapper users;
    @Autowired private AdminUserService service;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private PasswordEncoder encoder;
    @Autowired private MockMvc mvc;

    @Test
    void everyAdminResetRevokesPreviouslyIssuedTokens() throws Exception {
        jdbc.update("""
                INSERT INTO `user` (username,password,nickname,role,status,auth_version,deleted,update_time)
                VALUES ('reset-regression','test-only-hash','reset','USER',1,3,0,'2020-01-01 00:00:00')
                """);
        Long id = jdbc.queryForObject("SELECT id FROM `user` WHERE username='reset-regression'", Long.class);
        String oldToken = tokens.generateToken(id, "reset-regression", "USER", 3);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk());

        service.resetPassword(id, "test-only-new-password");

        assertEquals(4, users.selectById(id).getAuthVersion());
        assertTrue(encoder.matches("test-only-new-password", users.selectById(id).getPassword()));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        String replacement = tokens.generateToken(id, "reset-regression", "USER", 4);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + replacement))
                .andExpect(status().isOk());

        service.resetPassword(id, "test-only-another-password");
        assertEquals(5, users.selectById(id).getAuthVersion());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + replacement))
                .andExpect(status().isUnauthorized());
    }
}
