package com.learnplatform.service.understanding;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.security.CustomUserDetails;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Transactional
class QuestionUnderstandingPreviewIntegrationTest extends IntegrationTestBase {
    private static final long ADMIN = 989401L;
    @Autowired QuestionUnderstandingPreviewService preview;
    @Autowired QuestionUnderstandingService formal;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    private long questionId;

    @BeforeEach void setUp() {
        questionId = db.queryForObject("SELECT question_id FROM question_understanding_content WHERE source_title='系统种子生成：合法出栈序列'", Long.class);
        db.update("UPDATE question_understanding_hint SET review_status='PENDING',reviewed_by=NULL,reviewed_time=NULL WHERE content_id IN (SELECT id FROM question_understanding_content WHERE question_id=?)", questionId);
        db.update("INSERT INTO user(id,username,password,role,status,deleted) VALUES (?,'preview-admin','test','ADMIN',1,0)", ADMIN);
    }

    @Test void fullPreviewChangesNoRowsInAnyTableAndLeavesFormalAttemptUntouched() throws Exception {
        db.update("UPDATE question_understanding_content SET review_status='REVIEWED',reviewed_by='test-reviewer',reviewed_time=CURRENT_TIMESTAMP WHERE question_id=?", questionId);
        db.update("UPDATE ai_variant_question v JOIN question_ai_asset a ON a.id=v.asset_id SET v.review_status='APPROVED',v.reviewed_by=0,v.reviewed_time=CURRENT_TIMESTAMP WHERE a.question_id=?", questionId);
        long attempt = ((Number) formal.submit(ADMIN, questionId, body("{\"answer\":\"B\"}")).get("attemptId")).longValue();
        var formalBefore = formal.tutorNext(ADMIN, attempt);
        Map<String, List<String>> before = snapshot();
        var initial = call("{}");
        assertEquals(true, initial.get("preview"));
        assertNull(initial.get("attempt"));
        assertFalse(json.writeValueAsString(initial).contains("correctAnswer"));
        var answered = call("{\"answer\":\"B\"}");
        assertEquals("HINT", nested(answered, "next", "action", "type"));
        assertNull(answered.get("solution"));
        assertCode(1005, () -> call("{\"answer\":\"B\",\"revealSolution\":true}"));
        for (int level = 1; level <= 3; level++) {
            var response = call("{\"answer\":\"B\",\"hintLevel\":" + level + "}");
            var rows = (List<?>) response.get("hints");
            assertEquals(level, rows.size());
            for (Object row : rows) {
                assertEquals("PENDING", ((Map<?, ?>) ((Map<?, ?>) row).get("source")).get("reviewStatus"));
            }
        }
        var animation = call("{\"answer\":\"B\",\"hintLevel\":3}");
        assertEquals("PLAY_VIZ", nested(animation, "next", "action", "type"));
        var revealed = call("{\"answer\":\"B\",\"hintLevel\":3,\"misconceptionVizViewed\":true,\"revealSolution\":true}");
        assertEquals("A", nested(revealed, "solution", "correctAnswer"));
        assertNotNull(revealed.get("variant"));
        var completed = call("{\"answer\":\"B\",\"hintLevel\":3,\"misconceptionVizViewed\":true,\"revealSolution\":true,\"variantAnswer\":\"D\"}");
        assertEquals("AIDED", nested(completed, "variantResult", "assistance"));
        assertEquals(true, nested(completed, "variantResult", "correct"));
        assertEquals(completed, call("{\"answer\":\"B\",\"hintLevel\":3,\"misconceptionVizViewed\":true,\"revealSolution\":true,\"variantAnswer\":\"D\"}"));
        var correctPreview = call("{\"answer\":\"A\",\"revealSolution\":true,\"variantAnswer\":\"D\"}");
        assertEquals("UNAIDED", nested(correctPreview, "variantResult", "assistance"));
        assertEquals(before, snapshot(), "预览不得改变任何数据库表的任何行");
        assertEquals(formalBefore, formal.tutorNext(ADMIN, attempt));
        assertNull(db.queryForObject("SELECT solution_revealed_time FROM question_understanding_attempt WHERE id=?", Object.class, attempt));
    }

    @Test void pendingContentAndVariantsKeepTheirActualSourcesAndRemainHiddenPublicly() throws Exception {
        db.update("UPDATE question_understanding_content SET review_status='PENDING' WHERE question_id=?", questionId);
        db.update("UPDATE ai_variant_question SET review_status='PENDING' WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=?)", questionId);
        var before = snapshot();
        var response = call("{\"answer\":\"B\",\"hintLevel\":3,\"revealSolution\":true,\"variantAnswer\":\"D\"}");
        assertEquals("PENDING", nested(response, "attempt", "misconception", "source", "reviewStatus"));
        assertEquals("PENDING", nested(response, "attempt", "viz", "source", "reviewStatus"));
        assertEquals("PENDING", nested(response, "solution", "explanationSource", "reviewStatus"));
        assertEquals("PENDING", nested(response, "variant", "source", "verification"));
        assertEquals("PENDING", nested(response, "variantResult", "explanationSource", "reviewStatus"));
        assertCode(1004, () -> formal.question(ADMIN, questionId));
        assertEquals(before, snapshot());
    }

    @Test void rejectsOtherUsersAndInvalidOrWithdrawnResourcesWithoutWrites() throws Exception {
        var before = snapshot();
        assertCode(1004, () -> preview.preview(ADMIN, "USER", questionId, body("{}")));
        assertCode(1004, () -> preview.preview(ADMIN, "USER", -1, body("{}")));
        assertCode(1001, () -> call("{\"answer\":\"Z\"}"));
        assertCode(1001, () -> call("{\"hintLevel\":1}"));
        assertCode(1001, () -> call("{\"answer\":\"A\",\"hintLevel\":1}"));
        assertCode(1001, () -> call("{\"answer\":\"B\",\"variantAnswer\":\"D\"}"));
        assertEquals(before, snapshot());
        db.update("UPDATE question SET visibility='PRIVATE',owner_user_id=? WHERE id=?", ADMIN + 1, questionId);
        sqlSession.clearCache();
        assertCode(1004, () -> call("{}"));
        db.update("UPDATE question SET visibility='PUBLIC',owner_user_id=NULL,content=CONCAT(content,'changed') WHERE id=?", questionId);
        sqlSession.clearCache();
        assertCode(1004, () -> call("{}"));
    }

    @Test void previewStillRequiresPublishedCourseAndDoesNotReviveWithdrawnContent() {
        db.update("UPDATE course SET status=0 WHERE id=(SELECT course_id FROM question WHERE id=?)", questionId);
        assertCode(1004, () -> call("{}"));
        db.update("UPDATE course SET status=1 WHERE id=(SELECT course_id FROM question WHERE id=?)", questionId);
        db.update("UPDATE question_understanding_content SET review_status='WITHDRAWN' WHERE question_id=?", questionId);
        assertCode(1004, () -> call("{}"));
    }

    @Test void httpEndpointUsesAuthenticatedRoleAndUnifiedNotFoundCode() throws Exception {
        for (String role : List.of("ADMIN", "USER")) {
            var auth = new UsernamePasswordAuthenticationToken(new CustomUserDetails(ADMIN, "preview-admin", role),
                    null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            var response = mvc.perform(post("/api/understanding/preview/questions/" + questionId)
                    .with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
            response.andExpect(jsonPath("$.code").value("ADMIN".equals(role) ? 0 : 1004));
            if ("ADMIN".equals(role)) { response.andExpect(jsonPath("$.data.preview").value(true)); }
        }
    }

    private Map<String, List<String>> snapshot() throws Exception {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String table : db.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name", String.class)) {
            List<String> rows = new java.util.ArrayList<>();
            for (var row : db.queryForList("SELECT * FROM `" + table + "`")) { rows.add(json.writeValueAsString(row)); }
            rows.sort(String::compareTo);
            result.put(table, rows);
        }
        assertTrue(result.containsKey("user"));
        assertTrue(result.containsKey("course_learning_event"));
        return result;
    }

    private Object nested(Map<String, Object> map, String... keys) {
        Object value = map;
        for (String key : keys) { value = ((Map<?, ?>) value).get(key); }
        return value;
    }
    private JsonNode body(String value) { try { return json.readTree(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private Map<String, Object> call(String value) { return preview.preview(ADMIN, "ADMIN", questionId, body(value)); }
    private void assertCode(int code, Runnable action) {
        assertEquals(code, assertThrows(BusinessException.class, action::run).getCode());
    }
}
