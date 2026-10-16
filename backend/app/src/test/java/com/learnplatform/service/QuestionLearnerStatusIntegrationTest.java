package com.learnplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.IntegrationTestBase;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.understanding.QuestionUnderstandingService;
import com.learnplatform.service.understanding.QuestionUnderstandingPreviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@Transactional
class QuestionLearnerStatusIntegrationTest extends IntegrationTestBase {
    private static final long USER = 989601L;
    private static final long OTHER = 989602L;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired QuestionUnderstandingService understanding;
    @Autowired QuestionUnderstandingPreviewService preview;
    @Autowired WrongQuestionService wrong;
    @Autowired QuestionService questions;
    @Autowired org.mybatis.spring.SqlSessionTemplate sessions;
    private long questionId;

    @BeforeEach void setUp() {
        db.update("INSERT INTO user(id,username,password,role,status,deleted) VALUES (?,'status-user','test','USER',1,0),(?,'status-other','test','ADMIN',1,0)", USER, OTHER);
        questionId = db.queryForObject("SELECT question_id FROM question_understanding_content WHERE source_title='系统种子生成：合法出栈序列'", Long.class);
    }

    @Test void statusAndDueDateFollowOnlyCurrentUsersFormalAnswers() throws Exception {
        assertRow(USER, "USER", "NEW", null);
        preview.preview(OTHER, "ADMIN", questionId, json.createObjectNode().put("answer", "B"));
        assertRow(OTHER, "ADMIN", "NEW", null);
        understanding.submit(OTHER, questionId, json.createObjectNode().put("answer", "B"));
        assertRow(USER, "USER", "NEW", null);
        assertRow(OTHER, "ADMIN", "TO_REDO", dueDate(OTHER));
        understanding.submit(USER, questionId, json.createObjectNode().put("answer", "B"));
        assertRow(USER, "USER", "TO_REDO", dueDate(USER));
        var wrongPage = wrong.getWrongQuestions(USER, 1, 10, null, questionId, null, null);
        assertEquals(dueDate(USER), wrongPage.getRecords().getFirst().getNextReviewDate().toString());
        understanding.submit(USER, questionId, json.createObjectNode().put("answer", "A"));
        assertRow(USER, "USER", "DONE", null);
        assertRow(OTHER, "ADMIN", "TO_REDO", dueDate(OTHER));
    }

    @Test void unresolvedWrongTakesPriorityAndRemovedSchedulesHaveNoDate() throws Exception {
        wrong.addWrongQuestion(USER, questionId, "B");
        assertRow(USER, "USER", "TO_REDO", null);
        db.update("UPDATE wrong_question SET mastery_level=1 WHERE user_id=?", USER);
        assertRow(USER, "USER", "TO_REDO", null);
        understanding.submit(USER, questionId, json.createObjectNode().put("answer", "B"));
        db.update("UPDATE question_review_schedule SET next_review_date='2020-01-01' WHERE user_id=?", USER);
        assertRow(USER, "USER", "TO_REDO", "2020-01-01");
        db.update("UPDATE question_review_schedule SET deleted=1 WHERE user_id=?", USER);
        assertRow(USER, "USER", "TO_REDO", null);
        db.update("UPDATE wrong_question SET mastery_level=2 WHERE user_id=?", USER);
        assertRow(USER, "USER", "DONE", null);
        db.update("UPDATE wrong_question SET mastery_level=0,deleted=1 WHERE user_id=?", USER);
        assertRow(USER, "USER", "DONE", null);
    }

    @Test void oldPracticeAndUnscoredCardsDoNotInventIndependentEvidence() throws Exception {
        db.update("INSERT INTO practice_record(user_id,question_id,user_answer,is_correct) VALUES(?,?,'A',1)", USER, questionId);
        db.update("INSERT INTO question_review_schedule(user_id,question_id,next_review_date) VALUES(?,?,'2020-01-01')", USER, questionId);
        assertRow(USER, "USER", "NEW", null);
    }

    @Test void understandingFilterAppliesBeforePaginationAndCombinesWithScope() throws Exception {
        long course = db.queryForObject("SELECT course_id FROM question WHERE id=?", Long.class, questionId);
        long point = db.queryForObject("SELECT knowledge_point_id FROM question_understanding_content WHERE question_id=?", Long.class, questionId);
        var request = get("/api/questions").with(authentication(actor(USER, "USER")))
                .param("courseId", Long.toString(course)).param("knowledgePointId", Long.toString(point))
                .param("questionId", Long.toString(questionId)).param("hasUnderstanding", "true");
        mvc.perform(request).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].hasUnderstanding").value(true));
        mvc.perform(request.param("pageNum", "2")).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records").isEmpty());
        mvc.perform(get("/api/questions").with(authentication(actor(USER, "USER")))
                .param("questionId", Long.toString(questionId)).param("hasUnderstanding", "false"))
                .andExpect(jsonPath("$.data.total").value(0));
        db.update("UPDATE question_understanding_content SET review_status='PENDING' WHERE question_id=?", questionId);
        mvc.perform(get("/api/questions").with(authentication(actor(OTHER, "ADMIN")))
                .param("questionId", Long.toString(questionId)).param("hasUnderstanding", "true"))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/questions").with(authentication(actor(USER, "USER")))
                .param("questionId", Long.toString(questionId)).param("hasUnderstanding", "false"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].hasUnderstanding").value(false));
    }

    @Test void authenticationAndInvalidFilterKeepExistingErrorContract() throws Exception {
        mvc.perform(get("/api/questions")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
        mvc.perform(get("/api/questions").with(authentication(actor(USER, "USER")))
                .param("hasUnderstanding", "invalid"))
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test void increasingPageSizeDoesNotAddPerQuestionQueries() {
        for (Boolean filter : new Boolean[] {null, true, false}) {
            sessions.clearCache();
            long before = statementCount();
            questions.getEnabledQuestionPage(1, 1, null, null, null, null, null, filter, USER);
            long small = statementCount() - before;
            sessions.clearCache();
            before = statementCount();
            var large = questions.getEnabledQuestionPage(1, 50, null, null, null, null, null, filter, USER);
            long many = statementCount() - before;
            assertTrue(large.getRecords().size() > 1);
            assertTrue(many <= small + 1, "query count grew with page size: " + small + " -> " + many);
        }
    }

    private long statementCount() {
        return db.queryForObject("SHOW SESSION STATUS LIKE 'Questions'", (row, index) -> row.getLong(2));
    }

    private void assertRow(long userId, String role, String expected, String date) throws Exception {
        String response = mvc.perform(get("/api/questions").with(authentication(actor(userId, role)))
                        .param("questionId", Long.toString(questionId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode row = json.readTree(response).path("data").path("records").get(0);
        assertEquals(expected, row.path("learnerStatus").asText());
        assertTrue(row.has("redoDueDate"));
        assertEquals(date, row.path("redoDueDate").isNull() ? null : row.path("redoDueDate").asText());
    }

    private String dueDate(long userId) {
        return db.queryForObject("SELECT next_review_date FROM question_review_schedule WHERE user_id=? AND question_id=? AND deleted=0", java.sql.Date.class, userId, questionId).toString();
    }

    private UsernamePasswordAuthenticationToken actor(long id, String role) {
        return new UsernamePasswordAuthenticationToken(new CustomUserDetails(id, "status", role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
