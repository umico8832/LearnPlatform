package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.QuestionReviewRequest;
import com.learnplatform.dto.QuestionSubmissionRequest;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.support.MySqlRaceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration") @SpringBootTest @ActiveProfiles("integration")
@Import(SubmissionConcurrencyIntegrationTest.RaceConfiguration.class)
class SubmissionConcurrencyIntegrationTest extends IntegrationTestBase {
    @Autowired QuestionSubmissionService submissions;
    @Autowired QuestionSubmissionImportService imports;
    @Autowired JdbcTemplate db;
    @Autowired MySqlRaceSupport race;
    long course;
    long admin;

    @BeforeEach void setup() {
        db.update("INSERT INTO course(name,status,deleted) VALUES('D6 并发隔离课程',1,0)");
        course = db.queryForObject("SELECT MAX(id) FROM course WHERE name='D6 并发隔离课程'", Long.class);
        admin = db.queryForObject("SELECT id FROM user WHERE role='ADMIN' AND deleted=0 LIMIT 1", Long.class);
    }

    @AfterEach void cleanup() {
        for (long question : db.queryForList("SELECT id FROM question WHERE course_id=?", Long.class, course)) {
            for (String table : List.of("question_review_record", "question_version", "question_knowledge_point", "question_option")) {
                db.update("DELETE FROM " + table + " WHERE question_id=?", question);
            }
            db.update("DELETE FROM question WHERE id=?", question);
        }
        db.update("DELETE FROM question_submission WHERE course_id=?", course);
        db.update("DELETE FROM knowledge_point WHERE course_id=?", course);
        db.update("DELETE FROM course WHERE id=?", course);
    }

    @Test void twoOppositeReviewsHaveExactlyOneWinner() throws Exception {
        long id = submit();
        var outcomes = raceRead(() -> submissions.reviewSubmission(id, review(1), admin),
                () -> submissions.reviewSubmission(id, review(2), admin));
        assertEquals(1, outcomes.stream().filter(QuestionSubmissionVO.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(BusinessException.class::isInstance).count());
        var winner = (QuestionSubmissionVO) outcomes.stream().filter(QuestionSubmissionVO.class::isInstance)
                .findFirst().orElseThrow();
        assertEquals(winner.getStatus(), db.queryForObject("SELECT status FROM question_submission WHERE id=?", Integer.class, id));
    }

    @Test void twoImportsCreateExactlyOneCompleteQuestion() throws Exception {
        long id = submit();
        submissions.reviewSubmission(id, review(1), admin);
        var outcomes = raceRead(() -> imports.importSubmission(id, admin), () -> imports.importSubmission(id, admin));
        assertEquals(1, outcomes.stream().filter(QuestionSubmissionVO.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(BusinessException.class::isInstance).count());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM question WHERE course_id=?", Integer.class, course));
        assertEquals(3, db.queryForObject("SELECT status FROM question_submission WHERE id=?", Integer.class, id));
        long question = db.queryForObject("SELECT imported_question_id FROM question_submission WHERE id=?", Long.class, id);
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM question_option WHERE question_id=?", Integer.class, question));
    }

    @Test void failureAfterBuildingOptionsRollsBackQuestionAndClaimAndAllowsRetry() throws Exception {
        db.update("INSERT INTO knowledge_point(course_id,parent_id,name,deleted) VALUES(?,0,'D6 隔离知识点',0)", course);
        long point = db.queryForObject("SELECT MAX(id) FROM knowledge_point WHERE course_id=?", Long.class, course);
        long id = submit(Long.toString(point));
        submissions.reviewSubmission(id, review(1), admin);
        var childCounts = new java.util.LinkedHashMap<String, Integer>();
        for (String table : List.of("question_option", "question_knowledge_point", "question_review_record")) {
            childCounts.put(table, db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
        assertThrows(RuntimeException.class, () -> race.observe(new MySqlRaceSupport.StatementObserver() {
            @Override public void before(String statement, Connection connection) {
                if (statement.endsWith("QuestionKnowledgePointMapper.insert")) { throw new IllegalStateException("rollback fixture"); }
            }
        }, () -> imports.importSubmission(id, admin)));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM question WHERE course_id=?", Integer.class, course));
        assertEquals(1, db.queryForObject("SELECT status FROM question_submission WHERE id=?", Integer.class, id));
        childCounts.forEach((table, before) -> assertEquals(before,
                db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class), table + " must roll back"));
        assertNull(db.queryForObject("SELECT imported_question_id FROM question_submission WHERE id=?", Long.class, id));
        assertEquals(3, imports.importSubmission(id, admin).getStatus());
    }

    private List<Object> raceRead(Callable<?> first, Callable<?> second) throws Exception {
        var start = new CyclicBarrier(2);
        var read = new CyclicBarrier(2);
        var observer = new MySqlRaceSupport.StatementObserver() {
            @Override public void after(String statement, Connection connection) throws Exception {
                if (statement.endsWith("QuestionSubmissionMapper.selectById")) { read.await(10, TimeUnit.SECONDS); }
            }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> outcome(start, observer, first));
            var two = pool.submit(() -> outcome(start, observer, second));
            return List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        }
    }

    private Object outcome(CyclicBarrier start, MySqlRaceSupport.StatementObserver observer, Callable<?> action) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try { return race.observe(observer, action::call); }
        catch (BusinessException exception) { return exception; }
    }

    private long submit() { return submit(null); }
    private long submit(String points) {
        var request = new QuestionSubmissionRequest();
        request.setCourseId(course);
        request.setQuestionType("FILL_BLANK");
        request.setContent("D6 隔离并发测试投稿");
        request.setCorrectAnswer("fixture");
        request.setDifficulty(1);
        request.setKnowledgePointIds(points);
        return submissions.submitQuestion(request, admin).getId();
    }
    private QuestionReviewRequest review(int status) {
        var request = new QuestionReviewRequest();
        request.setStatus(status);
        request.setReviewComment("隔离并发测试");
        return request;
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class RaceConfiguration {
        @Bean MySqlRaceSupport submissionRaceSupport(Environment environment) { return new MySqlRaceSupport(environment); }
    }
}
