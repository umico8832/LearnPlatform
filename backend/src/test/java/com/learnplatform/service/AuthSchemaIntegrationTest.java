package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("integration")
@Transactional
@Tag("integration")
class AuthSchemaIntegrationTest extends IntegrationTestBase {
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void migrationAddsUniqueEmailAndAuthSecurityTables() {
        jdbcTemplate.update("""
                INSERT INTO `user` (`username`, `email`, `password`, `nickname`, `role`, `status`, `deleted`)
                VALUES (?, ?, ?, ?, 'USER', 1, 0)
                """, "auth-migration-a", "unique-auth@example.com", "hash", "A");

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update("""
                INSERT INTO `user` (`username`, `email`, `password`, `nickname`, `role`, `status`, `deleted`)
                VALUES (?, ?, ?, ?, 'USER', 1, 0)
                """, "auth-migration-b", "unique-auth@example.com", "hash", "B"));

        Integer verificationTables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('email_verification', 'password_reset_token')
                """, Integer.class);
        assertEquals(2, verificationTables);
    }
}
