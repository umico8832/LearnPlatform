package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.entity.Question;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Real MySQL coverage for the REQUIRES_NEW boundary used by Excel and Markdown imports. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class QuestionImportRowWriterIntegrationTest extends IntegrationTestBase {

    private static final String COURSE_NAME = "D 逐行导入事务隔离课程";

    @Autowired private QuestionImportRowWriter writer;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;

    private Long courseId;
    private Long knowledgePointId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO course(name,status,deleted) VALUES (?,1,0)", COURSE_NAME);
        courseId = jdbc.queryForObject(
                "SELECT id FROM course WHERE name=? ORDER BY id DESC LIMIT 1", Long.class, COURSE_NAME);
        jdbc.update("INSERT INTO knowledge_point(course_id,parent_id,name,deleted) VALUES (?,0,?,0)",
                courseId, "D 逐行导入事务隔离知识点");
        knowledgePointId = jdbc.queryForObject(
                "SELECT id FROM knowledge_point WHERE course_id=? ORDER BY id DESC LIMIT 1", Long.class, courseId);
    }

    @AfterEach
    void cleanUp() {
        if (courseId == null) {
            return;
        }
        List<Long> questionIds = jdbc.queryForList(
                "SELECT id FROM question WHERE course_id=?", Long.class, courseId);
        for (Long questionId : questionIds) {
            jdbc.update("DELETE FROM question_knowledge_point WHERE question_id=?", questionId);
            jdbc.update("DELETE FROM question_option WHERE question_id=?", questionId);
        }
        jdbc.update("DELETE FROM question WHERE course_id=?", courseId);
        jdbc.update("DELETE FROM knowledge_point WHERE course_id=?", courseId);
        jdbc.update("DELETE FROM course WHERE id=?", courseId);
    }

    @Test
    void rollsBackWholeFailedRowThenCommitsTheNextRow() {
        Question failed = question("D-ROW-FAILED");
        List<QuestionCreateRequest.OptionItem> failingOptions = List.of(
                option("A", "第一项已写入后必须随行回滚", 1, 1),
                option("B", null, 0, 2));

        assertThrows(DataIntegrityViolationException.class,
                () -> writer.write(failed, failingOptions, List.of(knowledgePointId)));

        assertNotNull(failed.getId());
        assertEquals(0, count("SELECT COUNT(*) FROM question WHERE id=?", failed.getId()));
        assertEquals(0, count("SELECT COUNT(*) FROM question_option WHERE question_id=?", failed.getId()));
        assertEquals(0, count("SELECT COUNT(*) FROM question_knowledge_point WHERE question_id=?", failed.getId()));

        Question succeeding = question("D-ROW-SUCCEEDED");
        writer.write(succeeding, List.of(
                option("A", "可提交的正确项", 1, 1),
                option("B", "可提交的错误项", 0, 2)), List.of(knowledgePointId));

        assertNotNull(succeeding.getId());
        assertEquals(1, count("SELECT COUNT(*) FROM question WHERE id=?", succeeding.getId()));
        assertEquals(2, count("SELECT COUNT(*) FROM question_option WHERE question_id=?", succeeding.getId()));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM question_knowledge_point
                WHERE question_id=? AND knowledge_point_id=?
                """, succeeding.getId(), knowledgePointId));
    }

    @Test
    void committedRowSurvivesOuterTransactionRollback() {
        Question question = question("D-ROW-REQUIRES-NEW");

        transactions.executeWithoutResult(status -> {
            writer.write(question, List.of(
                    option("A", "独立事务正确项", 1, 1),
                    option("B", "独立事务错误项", 0, 2)), List.of(knowledgePointId));
            status.setRollbackOnly();
        });

        assertNotNull(question.getId());
        assertEquals(1, count("SELECT COUNT(*) FROM question WHERE id=?", question.getId()));
        assertEquals(2, count("SELECT COUNT(*) FROM question_option WHERE question_id=?", question.getId()));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM question_knowledge_point
                WHERE question_id=? AND knowledge_point_id=?
                """, question.getId(), knowledgePointId));
    }

    private Question question(String content) {
        Question question = new Question();
        question.setContent(content);
        question.setQuestionType("SINGLE_CHOICE");
        question.setCourseId(courseId);
        question.setDifficulty(1);
        question.setAnalysis("逐行事务夹具");
        question.setScore(1);
        question.setStatus(1);
        question.setSourceType("EXCEL_IMPORT");
        question.setReviewRounds(0);
        question.setDeleted(0);
        return question;
    }

    private QuestionCreateRequest.OptionItem option(String label, String content, int correct, int sortOrder) {
        QuestionCreateRequest.OptionItem item = new QuestionCreateRequest.OptionItem();
        item.setOptionLabel(label);
        item.setContent(content);
        item.setIsCorrect(correct);
        item.setSortOrder(sortOrder);
        return item;
    }

    private int count(String sql, Object... values) {
        return jdbc.queryForObject(sql, Integer.class, values);
    }
}
