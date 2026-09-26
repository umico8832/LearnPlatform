package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.TutorCheckAnswerRequest;
import com.learnplatform.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class TutorCheckIntegrationTest extends IntegrationTestBase {
    private static final long USER = 989001L;
    private static final long OTHER_USER = 989002L;
    private static final long COURSE = 989010L;
    private static final long OTHER_COURSE = 989011L;
    private static final long POINT = 989020L;
    private static final long CONTENT = 989030L;
    private static final String SESSION = "00000000-0000-0000-0000-000000989001";

    @Autowired private TutorSessionService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("INSERT INTO user (id,username,password,role,status,deleted) VALUES (?,?,'test','USER',1,0)",
                USER, "tutor-check-owner");
        jdbc.update("INSERT INTO user (id,username,password,role,status,deleted) VALUES (?,?,'test','USER',1,0)",
                OTHER_USER, "tutor-check-other");
        jdbc.update("INSERT INTO course (id,name,status,deleted) VALUES (?,'Tutor 检查课程',1,0), (?,'其他课程',1,0)",
                COURSE, OTHER_COURSE);
        jdbc.update("INSERT INTO user_course (user_id,course_id) VALUES (?,?)", USER, COURSE);
        jdbc.update("INSERT INTO knowledge_point (id,name,course_id,content_review_status) VALUES (?,'Tutor 检查点',?,'REVIEWED')",
                POINT, COURSE);
        jdbc.update("""
                INSERT INTO tutor_content (id,knowledge_point_id,content_key,content_version,review_status,title,lesson_json,check_json)
                VALUES (?,?,'tutor-check-integration',1,'REVIEWED','Tutor 检查','{}',
                  '{"prompt":"选择正确项","options":[{"id":"A","text":"A"},{"id":"B","text":"B"}],"correctOptionId":"A","correctExplanation":"正确","incorrectExplanation":"错误"}')
                """, CONTENT, POINT);
        jdbc.update("""
                INSERT INTO tutor_session (session_key,user_id,course_id,knowledge_point_id,tutor_content_id,learning_context_json)
                VALUES (?,?,?,?,?, '{}')
                """, SESSION, USER, COURSE, POINT, CONTENT);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void concurrentDifferentOptionsPersistOneFirstAnswerAndOneCourseFact() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> answerA = () -> { start.await(); return answer("A"); };
        Callable<Boolean> answerB = () -> { start.await(); return answer("B"); };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> answers = List.of(executor.submit(answerA), executor.submit(answerB));
            start.countDown();
            boolean first = answers.get(0).get();
            boolean second = answers.get(1).get();
            assertEquals(first, second);
        }

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tutor_session WHERE session_key=? AND check_answer IS NOT NULL",
                Integer.class, SESSION));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM course_learning_event WHERE user_id=? AND course_id=? AND event_source='AI_TUTOR'",
                Integer.class, USER, COURSE));
    }

    @Test
    void rejectsWrongCourseWithdrawnContentAndRemovedCourseMembership() {
        assertThrows(BusinessException.class, () -> service.answer(USER, OTHER_COURSE, SESSION, request("A")));

        jdbc.update("UPDATE tutor_content SET review_status='REVIEW_PENDING' WHERE id=?", CONTENT);
        assertThrows(BusinessException.class, () -> service.answer(USER, COURSE, SESSION, request("A")));
        assertFalse(answered());

        jdbc.update("UPDATE tutor_content SET review_status='REVIEWED' WHERE id=?", CONTENT);
        jdbc.update("DELETE FROM user_course WHERE user_id=? AND course_id=?", USER, COURSE);
        assertThrows(BusinessException.class, () -> service.answer(USER, COURSE, SESSION, request("A")));
        assertFalse(answered());
    }

    @Test
    void checkEndpointRejectsAnotherAuthenticatedUserAndWrongCoursePath() throws Exception {
        String endpoint = "/api/my-courses/{courseId}/tutor-sessions/{sessionKey}/check";
        mvc.perform(post(endpoint, COURSE, SESSION).contentType("application/json").content("{\"optionId\":\"A\"}")
                        .with(authentication(learner(OTHER_USER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1004));
        mvc.perform(post(endpoint, OTHER_COURSE, SESSION).contentType("application/json").content("{\"optionId\":\"A\"}")
                        .with(authentication(learner(USER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1004));
        assertFalse(answered());
    }

    private boolean answer(String optionId) {
        return service.answer(USER, COURSE, SESSION, request(optionId)).isCorrect();
    }

    private TutorCheckAnswerRequest request(String optionId) {
        TutorCheckAnswerRequest request = new TutorCheckAnswerRequest();
        request.setOptionId(optionId);
        return request;
    }

    private boolean answered() {
        return jdbc.queryForObject("SELECT check_answer IS NOT NULL FROM tutor_session WHERE session_key=?",
                Boolean.class, SESSION);
    }

    private UsernamePasswordAuthenticationToken learner(long userId) {
        return new UsernamePasswordAuthenticationToken(new CustomUserDetails(userId, "learner", "USER"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private void cleanUp() {
        if (jdbc == null) return;
        jdbc.update("DELETE FROM course_learning_event WHERE user_id IN (?,?)", USER, OTHER_USER);
        jdbc.update("DELETE FROM tutor_agent_message WHERE run_id IN (SELECT id FROM tutor_agent_run WHERE tutor_session_id IN (SELECT id FROM tutor_session WHERE session_key=?))", SESSION);
        jdbc.update("DELETE FROM tutor_agent_run WHERE tutor_session_id IN (SELECT id FROM tutor_session WHERE session_key=?)", SESSION);
        jdbc.update("DELETE FROM tutor_session WHERE session_key=?", SESSION);
        jdbc.update("DELETE FROM tutor_content WHERE id=?", CONTENT);
        jdbc.update("DELETE FROM knowledge_point WHERE id=?", POINT);
        jdbc.update("DELETE FROM user_course WHERE user_id IN (?,?)", USER, OTHER_USER);
        jdbc.update("DELETE FROM course WHERE id IN (?,?)", COURSE, OTHER_COURSE);
        jdbc.update("DELETE FROM user WHERE id IN (?,?)", USER, OTHER_USER);
    }
}
