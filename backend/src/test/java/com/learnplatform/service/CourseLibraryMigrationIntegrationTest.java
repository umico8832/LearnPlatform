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
                "SELECT COUNT(*) FROM knowledge_point WHERE course_id = ? AND content_source = 'AISTU' AND parent_id = 0",
                Integer.class,
                courseId);

        assertEquals(8, chapterCount);
        Integer atomicKnowledgeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_point WHERE course_id = ? AND content_key = ? "
                        + "AND content_source = 'AISTU' AND content_version = 1 "
                        + "AND content_review_status = 'REVIEWED'",
                Integer.class,
                courseId,
                "ods-arraystack-insertion");
        assertEquals(1, atomicKnowledgeCount);
        Integer reviewedTutorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_point WHERE course_id = ? AND content_key IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "AND content_source = 'AISTU' AND content_version = 1 AND content_review_status = 'REVIEWED'",
                Integer.class,
                courseId,
                "ods-arraystack-insertion",
                "ods-arraystack-removal",
                "ods-array-size-capacity",
                "ods-arraystack-resize",
                "ods-arraystack-amortized-resize",
                "ods-arraystack-performance",
                "ods-fastarraystack-block-copy",
                "ods-arrayqueue-representation",
                "ods-arrayqueue-enqueue",
                "ods-arrayqueue-dequeue",
                "ods-arrayqueue-resize",
                "ods-arrayqueue-performance",
                "ods-arraydeque-representation",
                "ods-arraydeque-nearest-end-shifting",
                "ods-arraydeque-performance",
                "ods-dualarraydeque-representation",
                "ods-dualarraydeque-end-operations",
                "ods-dualarraydeque-balance",
                "ods-dualarraydeque-amortized-balance",
                "ods-dualarraydeque-performance",
                "ods-rootisharraystack-block-layout",
                "ods-rootisharraystack-index-mapping",
                "ods-rootisharraystack-update",
                "ods-rootisharraystack-grow-shrink",
                "ods-rootisharraystack-space",
                "ods-rootisharraystack-performance",
                "ods-array-layout-tradeoffs",
                "cs408-linear-list-definition-operations",
                "cs408-sequential-list-storage",
                "cs408-sequential-list-insert-delete",
                "cs408-singly-linked-list",
                "cs408-linked-list-insert-delete");
        assertEquals(32, reviewedTutorCount);
        String coursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arraystack-insertion");
        assertEquals("ARRAY_STACK_INSERTION", coursewareKind);
        String resizeCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arraystack-resize");
        assertEquals("ARRAY_STACK_RESIZE", resizeCoursewareKind);
        String queueCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arrayqueue-representation");
        assertEquals("ARRAY_QUEUE_REPRESENTATION", queueCoursewareKind);
        String enqueueCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arrayqueue-enqueue");
        assertEquals("ARRAY_QUEUE_ENQUEUE", enqueueCoursewareKind);
        String dequeueCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arrayqueue-dequeue");
        assertEquals("ARRAY_QUEUE_DEQUEUE", dequeueCoursewareKind);
        String queueResizeCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arrayqueue-resize");
        assertEquals("ARRAY_QUEUE_RESIZE", queueResizeCoursewareKind);
        String dequeCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arraydeque-representation");
        assertEquals("ARRAY_DEQUE_REPRESENTATION", dequeCoursewareKind);
        String dequeShiftCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arraydeque-nearest-end-shifting");
        assertEquals("ARRAY_DEQUE_FRONT_SHIFT_INSERT", dequeShiftCoursewareKind);
        String dualDequeCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-dualarraydeque-representation");
        assertEquals("DUAL_ARRAY_DEQUE_REPRESENTATION", dualDequeCoursewareKind);
        String dualDequeBalanceCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-dualarraydeque-balance");
        assertEquals("DUAL_ARRAY_DEQUE_BALANCE", dualDequeBalanceCoursewareKind);
        String rootishLayoutCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-rootisharraystack-block-layout");
        assertEquals("ROOTISH_ARRAY_STACK_LAYOUT", rootishLayoutCoursewareKind);
        String sequentialStorageCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "cs408-sequential-list-storage");
        assertEquals("SEQUENTIAL_LIST_STORAGE", sequentialStorageCoursewareKind);
        String removalCorrectOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arraystack-removal");
        assertEquals("LEFT_TO_RIGHT", removalCorrectOption);
        String amortizedCorrectOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arraystack-amortized-resize");
        assertEquals("TOTAL_LINEAR", amortizedCorrectOption);
        String performanceCorrectOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arraystack-performance");
        assertEquals("TAIL_AMORTIZED", performanceCorrectOption);
        String fastArrayStackCorrectOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-fastarraystack-block-copy");
        assertEquals("CONSTANT_FACTOR_ONLY", fastArrayStackCorrectOption);
        String enqueueCorrectOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arrayqueue-enqueue");
        assertEquals("TAIL_SLOT", enqueueCorrectOption);
        String dequeueCorrectOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arrayqueue-dequeue");
        assertEquals("ADVANCE_HEAD", dequeueCorrectOption);
        String queueResizeCorrectOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arrayqueue-resize");
        assertEquals("COPY_LOGICAL_ORDER", queueResizeCorrectOption);
        String queuePerformanceCorrectOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arrayqueue-performance");
        assertEquals("QUEUE_AMORTIZED", queuePerformanceCorrectOption);
        String dequeRepresentationCorrectOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arraydeque-representation");
        assertEquals("INDEX_ONE", dequeRepresentationCorrectOption);
        String dequeShiftCorrectOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arraydeque-nearest-end-shifting");
        assertEquals("SHIFT_FRONT", dequeShiftCorrectOption);
        String dequePerformanceCorrectOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-arraydeque-performance");
        assertEquals("NEAREST_END", dequePerformanceCorrectOption);
        String dualDequeCorrectOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "ods-dualarraydeque-representation");
        assertEquals("FRONT_REVERSE", dualDequeCorrectOption);
        assertCorrectOption("ods-dualarraydeque-end-operations", "ROUTE_FRONT");
        assertCorrectOption("ods-dualarraydeque-balance", "REBUILD_HALVES");
        assertCorrectOption("ods-dualarraydeque-amortized-balance", "TOTAL_LINEAR");
        assertCorrectOption("ods-dualarraydeque-performance", "NEAREST_END");
        assertCorrectOption("ods-rootisharraystack-block-layout", "INCREASING_BLOCK");
        assertCorrectOption("ods-rootisharraystack-index-mapping", "PREVIOUS_CAPACITY");
        assertCorrectOption("ods-rootisharraystack-update", "RIGHT_TO_LEFT");
        assertCorrectOption("ods-rootisharraystack-grow-shrink", "NO_COPY");
        assertCorrectOption("ods-rootisharraystack-space", "BLOCK_COUNT");
        assertCorrectOption("ods-rootisharraystack-performance", "SQRT_SPACE");
        assertCorrectOption("ods-array-layout-tradeoffs", "SPACE_TRADEOFF");
        assertCorrectOption("cs408-linear-list-definition-operations", "UNIQUE_NEIGHBORS");
        assertCorrectOption("cs408-sequential-list-storage", "OFFSET_EIGHT");
        assertCorrectOption("cs408-sequential-list-insert-delete", "AVOID_OVERWRITE");
        assertCorrectOption("cs408-singly-linked-list", "TRAVERSE_NEXT");
        assertCorrectOption("cs408-linked-list-insert-delete", "CONNECT_SUCCESSOR");
        assertArrayQueuePath("ods-arrayqueue-representation", null, "ods-arrayqueue-enqueue");
        assertArrayQueuePath("ods-arrayqueue-enqueue", "ods-arrayqueue-representation", "ods-arrayqueue-dequeue");
        assertArrayQueuePath("ods-arrayqueue-dequeue", "ods-arrayqueue-representation", "ods-arrayqueue-resize");
        assertArrayQueuePath("ods-arrayqueue-resize", "ods-arrayqueue-representation", "ods-arrayqueue-performance");
        assertArrayQueuePath("ods-arrayqueue-performance", "ods-arrayqueue-resize", null);
        assertArrayDequePath("ods-arraydeque-representation", "ods-arrayqueue-representation", "ods-arraydeque-nearest-end-shifting");
        assertArrayDequePath("ods-arraydeque-nearest-end-shifting", "ods-arraydeque-representation", "ods-arraydeque-performance");
        assertArrayDequePath("ods-arraydeque-performance", "ods-arraydeque-nearest-end-shifting", null);
        assertArrayDequePath("ods-dualarraydeque-representation", "ods-arraydeque-representation", "ods-dualarraydeque-end-operations");
        assertArrayDequePath("ods-dualarraydeque-end-operations", "ods-dualarraydeque-representation", "ods-dualarraydeque-balance");
        assertArrayDequePath("ods-dualarraydeque-balance", "ods-dualarraydeque-end-operations", "ods-dualarraydeque-amortized-balance");
        assertArrayDequePath("ods-dualarraydeque-amortized-balance", "ods-dualarraydeque-balance", "ods-dualarraydeque-performance");
        assertArrayDequePath("ods-dualarraydeque-performance", "ods-dualarraydeque-amortized-balance", null);
        assertArrayDequePath("ods-rootisharraystack-block-layout", "ods-arraydeque-representation", "ods-rootisharraystack-index-mapping");
        assertArrayDequePath("ods-rootisharraystack-index-mapping", "ods-rootisharraystack-block-layout", "ods-rootisharraystack-update");
        assertArrayDequePath("ods-rootisharraystack-update", "ods-rootisharraystack-index-mapping", "ods-rootisharraystack-grow-shrink");
        assertArrayDequePath("ods-rootisharraystack-grow-shrink", "ods-rootisharraystack-update", "ods-rootisharraystack-space");
        assertArrayDequePath("ods-rootisharraystack-space", "ods-rootisharraystack-grow-shrink", "ods-rootisharraystack-performance");
        assertArrayDequePath("ods-rootisharraystack-performance", "ods-rootisharraystack-space", null);

        jdbcTemplate.update(
                "INSERT INTO user_course (user_id, course_id) VALUES (?, ?)",
                90001L,
                courseId);
        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update(
                "INSERT INTO user_course (user_id, course_id) VALUES (?, ?)",
                90001L,
                courseId));

        jdbcTemplate.update("""
                INSERT INTO course_learning_event
                    (user_id, course_id, event_type, event_source, subject_type, subject_id,
                     source_record_id, idempotency_key, event_version, occurred_time)
                VALUES (?, ?, 'PRACTICE_ANSWERED', 'PRACTICE', 'QUESTION', ?, ?, ?, 1, CURRENT_TIMESTAMP)
                """, 90001L, courseId, 80001L, 70001L, "PRACTICE:70001");
        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update("""
                INSERT INTO course_learning_event
                    (user_id, course_id, event_type, event_source, subject_type, subject_id,
                     source_record_id, idempotency_key, event_version, occurred_time)
                VALUES (?, ?, 'PRACTICE_ANSWERED', 'PRACTICE', 'QUESTION', ?, ?, ?, 1, CURRENT_TIMESTAMP)
                """, 90001L, courseId, 80001L, 70001L, "PRACTICE:70001"));
    }

    private void assertArrayQueuePath(String contentKey, String prerequisiteKey, String nextStepKey) {
        String prerequisite = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.prerequisite.contentKey')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                contentKey);
        String nextStep = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.nextStep.contentKey')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                contentKey);
        assertEquals(prerequisiteKey, prerequisite);
        assertEquals(nextStepKey, nextStep);
    }

    private void assertArrayDequePath(String contentKey, String prerequisiteKey, String nextStepKey) {
        assertArrayQueuePath(contentKey, prerequisiteKey, nextStepKey);
    }

    private void assertCorrectOption(String contentKey, String expectedOptionId) {
        String correctOption = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(check_json, '$.correctOptionId')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                contentKey);
        assertEquals(expectedOptionId, correctOption);
    }
}
