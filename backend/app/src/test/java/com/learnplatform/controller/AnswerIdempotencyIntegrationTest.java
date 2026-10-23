package com.learnplatform.controller;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.understanding.QuestionUnderstandingService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Tag("integration") @SpringBootTest @ActiveProfiles("integration")
class AnswerIdempotencyIntegrationTest extends IntegrationTestBase {
    static final long USER = 88119L;
    @Autowired PracticeController practice;
    @Autowired QuestionSubmissionController submissions;
    @Autowired ReviewController review;
    @Autowired QuestionUnderstandingController understanding;
    @Autowired QuestionUnderstandingService understandingService;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    MockMvc mvc;
    long question;

    @BeforeEach void setup() {
        cleanup();
        db.update("INSERT INTO user(id,username,password,role,status,deleted) VALUES(?,'d2-http-fixture','test','USER',1,0)", USER);
        question = db.queryForObject("SELECT question_id FROM question_understanding_content WHERE source_title='系统种子生成：合法出栈序列'", Long.class);
        long course = db.queryForObject("SELECT course_id FROM question WHERE id=?", Long.class, question);
        db.update("INSERT INTO user_course(user_id,course_id) VALUES(?,?)", USER, course);
        mvc = MockMvcBuilders.standaloneSetup(practice, review, understanding, submissions)
                .setMessageConverters(new JacksonJsonHttpMessageConverter((tools.jackson.databind.json.JsonMapper) json))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver()).build();
    }

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        if (db == null) { return; }
        for (String table : List.of("http_idempotency_record", "question_submission", "gamification_achievement", "gamification_xp_ledger",
                "gamification_user_profile", "fsrs_review_event", "course_learning_event",
                "question_understanding_attempt", "practice_record", "wrong_question", "question_review_schedule",
                "user_course", "user")) {
            db.update("DELETE FROM " + table + " WHERE " + (table.equals("user") ? "id" : "user_id") + "=?", USER);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"understanding", "practice", "review"})
    void concurrentAndLostResponseRetriesReturnFirstDataAndCreateOneLearningFact(String operation) throws Exception {
        if (operation.equals("review")) {
            understandingService.submit(USER, question, json.readTree("{\"answer\":\"A\"}"));
        }
        String path = operation.equals("understanding") ? "/api/understanding/questions/" + question + "/attempts"
                : "/api/" + operation + "/submit";
        String body = operation.equals("understanding") ? "{\"answer\":\"A\",\"answerTime\":3}"
                : "{\"questionId\":" + question + ",\"userAnswer\":\"A\",\"answerTime\":3}";
        String key = UUID.randomUUID().toString();
        Map<String, Integer> before = counts();
        var barrier = new CyclicBarrier(2);
        JsonNode first;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return submit(path, key, body); });
            var two = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return submit(path, key, body); });
            first = one.get(30, TimeUnit.SECONDS);
            assertEquals(0, first.path("code").asInt());
            assertEquals(first.path("data"), two.get(30, TimeUnit.SECONDS).path("data"));
        }
        assertEquals(first.path("data"), submit(path, key, body).path("data"), "Response loss must not create another answer");
        Map<String, Integer> after = counts();
        for (String table : before.keySet()) {
            int increment = table.equals("question_understanding_attempt") && !operation.equals("understanding") ? 0 : 1;
            assertEquals(before.get(table) + increment, after.get(table), table + " / " + operation);
        }
        assertEquals(ResultCode.BUSINESS_ERROR.getCode(), submit(path, key, body.replace("\"A\"", "\"B\"")).path("code").asInt());
        assertEquals(after, counts(), "Conflicting reuse must not write any fact");
    }

    @Test void aSubmissionIntentIsCreatedOnceAndReplayedAfterResponseLoss() throws Exception {
        long course = db.queryForObject("SELECT course_id FROM question WHERE id=?", Long.class, question);
        String body = "{\"content\":\"D6 隔离投稿\",\"questionType\":\"FILL_BLANK\",\"courseId\":" + course
                + ",\"difficulty\":1,\"correctAnswer\":\"fixture\"}";
        String key = UUID.randomUUID().toString();
        var barrier = new CyclicBarrier(2);
        JsonNode first;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return submit("/api/submission", key, body); });
            var two = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return submit("/api/submission", key, body); });
            first = one.get(30, TimeUnit.SECONDS);
            assertEquals(0, first.path("code").asInt());
            assertEquals(first.path("data"), two.get(30, TimeUnit.SECONDS).path("data"));
        }
        assertEquals(first.path("data"), submit("/api/submission", key, body).path("data"));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM question_submission WHERE user_id=?", Integer.class, USER));
        assertEquals(ResultCode.BUSINESS_ERROR.getCode(), submit("/api/submission", key,
                body.replace("D6 隔离投稿", "D6 已修改投稿")).path("code").asInt());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM question_submission WHERE user_id=?", Integer.class, USER));
    }

    private JsonNode submit(String path, String key, String body) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(USER, "d2-http-fixture", "USER"), null, List.of()));
        try {
            var result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", key).content(body)).andReturn();
            assertEquals(200, result.getResponse().getStatus());
            return json.readTree(result.getResponse().getContentAsString());
        } finally { SecurityContextHolder.clearContext(); }
    }

    private Map<String, Integer> counts() {
        var counts = new LinkedHashMap<String, Integer>();
        for (String table : List.of("practice_record", "question_understanding_attempt", "fsrs_review_event",
                "course_learning_event", "gamification_xp_ledger")) {
            counts.put(table, db.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id=?", Integer.class, USER));
        }
        return counts;
    }
}
