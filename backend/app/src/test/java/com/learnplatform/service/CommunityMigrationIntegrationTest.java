package com.learnplatform.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@Testcontainers
class CommunityMigrationIntegrationTest {
    @Container private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("learn_platform").withUsername("test").withPassword("test");

    @Test void upgradesExistingDatabaseWithoutChangingCoursesAndEnforcesUniqueInteractions() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).target("97").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
        Long courses = jdbc.queryForObject("SELECT COUNT(*) FROM course", Long.class);
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()).load().migrate();
        assertEquals(courses, jdbc.queryForObject("SELECT COUNT(*) FROM course", Long.class));
        assertEquals("computer-science-408", jdbc.queryForObject("SELECT parent_id FROM community_category WHERE id='data-structures'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM community_post", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM community_category WHERE kind='SCHOOL'", Integer.class));
        jdbc.update("INSERT INTO community_like (post_id,comment_id,user_id) VALUES (1,0,2)");
        assertThrows(DataAccessException.class, () -> jdbc.update("INSERT INTO community_like (post_id,comment_id,user_id) VALUES (1,0,2)"));
        jdbc.update("INSERT INTO community_question_link (post_id,submission_id,request_key) VALUES (1,1,'retry-key')");
        assertThrows(DataAccessException.class, () -> jdbc.update("INSERT INTO community_question_link (post_id,submission_id,request_key) VALUES (1,2,'retry-key')"));
    }
}
