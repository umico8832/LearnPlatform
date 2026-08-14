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
    void migrationAddsTutorLearningContextSnapshot() {
        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'tutor_session'
                  AND column_name = 'learning_context_json' AND data_type = 'json'
                """, Integer.class);

        assertEquals(1, columnCount);
    }

    @Test
    void migrationCreatesTraceableExamLearningAiInteractionTable() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'exam_learning_ai_interaction'
                """, Integer.class);
        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'exam_learning_ai_interaction'
                  AND column_name IN ('id', 'user_id', 'course_id', 'exam_paper_id',
                                      'learning_session_id', 'question_id', 'answer_id',
                                      'answer_attempt_no', 'answer_correct', 'interaction_type',
                                      'status', 'error_message', 'start_time', 'complete_time', 'create_time')
                """, Integer.class);

        assertEquals(1, tableCount);
        assertEquals(15, columnCount);
    }

    @Test
    void migrationImportsVerified2026Cs408DataStructureObjectivePaper() {
        Long courseId = jdbcTemplate.queryForObject(
                "SELECT id FROM course WHERE content_key = ?",
                Long.class,
                "cs408-data-structures");
        Long paperId = jdbcTemplate.queryForObject("""
                SELECT id FROM exam_paper
                WHERE course_id = ? AND paper_type = 'OFFICIAL_EXAM'
                  AND exam_year = 2026 AND title = '2026 年 408 真题·数据结构选择题'
                  AND source_reference = 'https://csgraduates.com/study_methods/408quiz/2026/'
                  AND source_verified = 1 AND status = 1 AND deleted = 0
                """, Long.class, courseId);

        Integer questionCount = jdbcTemplate.queryForObject(
                "SELECT question_count FROM exam_paper WHERE id = ?", Integer.class, paperId);
        Integer totalScore = jdbcTemplate.queryForObject(
                "SELECT total_score FROM exam_paper WHERE id = ?", Integer.class, paperId);
        Integer linkedQuestionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM exam_question eq
                JOIN question q ON q.id = eq.question_id
                WHERE eq.exam_paper_id = ? AND q.course_id = ? AND q.status = 1 AND q.deleted = 0
                  AND eq.section_title = '一、单项选择题（数据结构）'
                  AND eq.display_number = CONCAT('第', eq.minor_question_number, '题')
                """, Integer.class, paperId, courseId);
        Integer optionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM question_option qo
                JOIN exam_question eq ON eq.question_id = qo.question_id
                WHERE eq.exam_paper_id = ? AND qo.deleted = 0
                """, Integer.class, paperId);
        Integer correctOptionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM question_option qo
                JOIN exam_question eq ON eq.question_id = qo.question_id
                WHERE eq.exam_paper_id = ? AND qo.deleted = 0 AND qo.is_correct = 1
                """, Integer.class, paperId);
        String answerKey = jdbcTemplate.queryForObject("""
                SELECT GROUP_CONCAT(qo.option_label ORDER BY eq.sort_order SEPARATOR ',')
                FROM question_option qo
                JOIN exam_question eq ON eq.question_id = qo.question_id
                WHERE eq.exam_paper_id = ? AND qo.deleted = 0 AND qo.is_correct = 1
                """, String.class, paperId);
        Integer knowledgeMappedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT qkp.question_id) FROM question_knowledge_point qkp
                JOIN exam_question eq ON eq.question_id = qkp.question_id
                WHERE eq.exam_paper_id = ?
                """, Integer.class, paperId);

        assertEquals(11, questionCount);
        assertEquals(22, totalScore);
        assertEquals(11, linkedQuestionCount);
        assertEquals(44, optionCount);
        assertEquals(11, correctOptionCount);
        assertEquals("A,D,C,B,D,D,B,D,B,A,C", answerKey);
        assertEquals(11, knowledgeMappedCount);
    }

    @Test
    void migrationImportsAiStuCourseStructureAndProtectsLibraryUniqueness() {
        Integer paperProvenanceColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'exam_paper'
                  AND column_name IN ('paper_type', 'exam_name', 'exam_year',
                                      'source_reference', 'source_verified')
                """, Integer.class);
        Integer questionStructureColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'exam_question'
                  AND column_name IN ('section_title', 'major_question_number',
                                      'minor_question_number', 'subquestion_number', 'display_number')
                """, Integer.class);
        assertEquals(5, paperProvenanceColumns);
        assertEquals(5, questionStructureColumns);
        Integer learningSessionTables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('exam_learning_session', 'exam_learning_answer')
                """, Integer.class);
        Integer learningSessionColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND (
                    (table_name = 'exam_learning_session'
                     AND column_name IN ('user_id', 'exam_paper_id', 'status', 'current_question_id',
                                         'active_session_key', 'start_time', 'complete_time'))
                    OR
                    (table_name = 'exam_learning_answer'
                     AND column_name IN ('session_id', 'question_id', 'attempt_no', 'user_answer',
                                         'is_correct', 'score', 'answer_time', 'create_time'))
                )
                """, Integer.class);
        assertEquals(2, learningSessionTables);
        assertEquals(15, learningSessionColumns);
        Integer activeExamColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'exam_record'
                  AND column_name = 'active_exam_key'
                """, Integer.class);
        assertEquals(1, activeExamColumns);

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
                "SELECT COUNT(*) FROM knowledge_point WHERE course_id = ? AND content_key IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
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
                "cs408-linked-list-insert-delete",
                "cs408-doubly-linked-list",
                "cs408-circular-linked-list",
                "cs408-linked-list-reversal",
                "cs408-linked-list-merge",
                "cs408-sequential-vs-linked",
                "cs408-stack-lifo",
                "cs408-sequential-stack",
                "cs408-linked-stack",
                "cs408-stack-pop-sequences",
                "cs408-parentheses-matching",
                "cs408-expression-evaluation",
                "cs408-recursion-call-stack",
                "cs408-queue-fifo",
                "cs408-circular-queue-representation",
                "cs408-circular-queue-state",
                "cs408-linked-queue");
        assertEquals(48, reviewedTutorCount);
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
        String linkedListReversalCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "cs408-linked-list-reversal");
        assertEquals("LINKED_LIST_REVERSAL", linkedListReversalCoursewareKind);
        String factorialCallStackCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "cs408-recursion-call-stack");
        assertEquals("FACTORIAL_CALL_STACK", factorialCallStackCoursewareKind);
        String circularQueueCoursewareKind = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(lesson_json, '$.visualization.kind')) "
                        + "FROM tutor_content WHERE content_key = ? AND content_version = 1",
                String.class,
                "cs408-circular-queue-state");
        assertEquals("ARRAY_QUEUE_REPRESENTATION", circularQueueCoursewareKind);
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
        assertCorrectOption("cs408-doubly-linked-list", "PAIR_INVARIANTS");
        assertCorrectOption("cs408-circular-linked-list", "RETURN_TO_START");
        assertCorrectOption("cs408-linked-list-reversal", "SAVE_NEXT");
        assertCorrectOption("cs408-linked-list-merge", "LEFT_ON_EQUAL");
        assertCorrectOption("cs408-sequential-vs-linked", "OPERATION_PROFILE");
        assertCorrectOption("cs408-stack-lifo", "TOP_ONLY");
        assertCorrectOption("cs408-sequential-stack", "KEEP_CONVENTION");
        assertCorrectOption("cs408-linked-stack", "HEAD_PUSH");
        assertCorrectOption("cs408-stack-pop-sequences", "SIMULATE_TOP");
        assertCorrectOption("cs408-parentheses-matching", "STACK_EMPTY_END");
        assertCorrectOption("cs408-expression-evaluation", "RIGHT_THEN_LEFT");
        assertCorrectOption("cs408-recursion-call-stack", "INNER_RETURNS_FIRST");
        assertCorrectOption("cs408-queue-fifo", "FIFO_ENDS");
        assertCorrectOption("cs408-circular-queue-representation", "MODULO_WRAP");
        assertCorrectOption("cs408-circular-queue-state", "ONE_EMPTY_SLOT");
        assertCorrectOption("cs408-linked-queue", "RESET_REAR");
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
        assertArrayDequePath("cs408-doubly-linked-list", "cs408-linked-list-insert-delete", "cs408-circular-linked-list");
        assertArrayDequePath("cs408-circular-linked-list", "cs408-singly-linked-list", "cs408-linked-list-reversal");
        assertArrayDequePath("cs408-linked-list-reversal", "cs408-singly-linked-list", "cs408-linked-list-merge");
        assertArrayDequePath("cs408-linked-list-merge", "cs408-singly-linked-list", "cs408-sequential-vs-linked");
        assertArrayDequePath("cs408-sequential-vs-linked", "cs408-sequential-list-storage", null);
        assertArrayDequePath("cs408-stack-lifo", "cs408-linear-list-definition-operations", "cs408-sequential-stack");
        assertArrayDequePath("cs408-sequential-stack", "cs408-stack-lifo", "cs408-linked-stack");
        assertArrayDequePath("cs408-linked-stack", "cs408-stack-lifo", "cs408-stack-pop-sequences");
        assertArrayDequePath("cs408-stack-pop-sequences", "cs408-stack-lifo", null);
        assertArrayDequePath("cs408-parentheses-matching", "cs408-stack-lifo", "cs408-expression-evaluation");
        assertArrayDequePath("cs408-expression-evaluation", "cs408-stack-lifo", "cs408-recursion-call-stack");
        assertArrayDequePath("cs408-recursion-call-stack", "cs408-stack-lifo", null);
        assertArrayDequePath("cs408-queue-fifo", "cs408-linear-list-definition-operations", "cs408-circular-queue-representation");
        assertArrayDequePath("cs408-circular-queue-representation", "cs408-queue-fifo", "cs408-circular-queue-state");
        assertArrayDequePath("cs408-circular-queue-state", "cs408-circular-queue-representation", "cs408-linked-queue");
        assertArrayDequePath("cs408-linked-queue", "cs408-queue-fifo", null);

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

    @Test
    void activeExamKeyAllowsOnlyOneRecoverableRecordUntilReleased() {
        jdbcTemplate.update("""
                INSERT INTO exam_record
                    (user_id, exam_paper_id, start_time, total_score, status, active_exam_key)
                VALUES (99001, 99002, CURRENT_TIMESTAMP, 100, 0, 'EXAM:99001:99002')
                """);

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update("""
                INSERT INTO exam_record
                    (user_id, exam_paper_id, start_time, total_score, status, active_exam_key)
                VALUES (99001, 99002, CURRENT_TIMESTAMP, 100, 0, 'EXAM:99001:99002')
                """));

        jdbcTemplate.update("""
                UPDATE exam_record
                SET status = 1, end_time = CURRENT_TIMESTAMP, active_exam_key = NULL
                WHERE active_exam_key = 'EXAM:99001:99002'
                """);
        jdbcTemplate.update("""
                INSERT INTO exam_record
                    (user_id, exam_paper_id, start_time, total_score, status, active_exam_key)
                VALUES (99001, 99002, CURRENT_TIMESTAMP, 100, 0, 'EXAM:99001:99002')
                """);

        Integer activeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM exam_record
                WHERE active_exam_key = 'EXAM:99001:99002' AND status = 0
                """, Integer.class);
        assertEquals(1, activeCount);
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
