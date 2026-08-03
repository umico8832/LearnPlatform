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

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class CourseLibraryMigrationIntegrationTest extends IntegrationTestBase {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void migrationImportsAiStuCourseStructureAndProtectsLibraryUniqueness() {
        Long courseId = jdbcTemplate.queryForObject(
                "SELECT id FROM course WHERE content_key = ?",
                Long.class,
                "cs408-data-structures");
        Integer chapterCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_point WHERE course_id = ? AND content_source = 'AISTU'",
                Integer.class,
                courseId);

        assertEquals(8, chapterCount);

        jdbcTemplate.update(
                "INSERT INTO user_course (user_id, course_id) VALUES (?, ?)",
                90001L,
                courseId);
        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update(
                "INSERT INTO user_course (user_id, course_id) VALUES (?, ?)",
                90001L,
                courseId));
    }
}
