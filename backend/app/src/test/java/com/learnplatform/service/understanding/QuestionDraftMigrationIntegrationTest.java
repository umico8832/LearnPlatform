package com.learnplatform.service.understanding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
class QuestionDraftMigrationIntegrationTest {
    @Test void migratesEmptyDatabase() throws Exception { migrate(null); }
    @Test void upgradesCurrentV111AndPreservesUnrelatedRowsAndLearningFacts() throws Exception { migrate("111"); }

    @Test void upgradesCurrentV112AndPreservesLearningFacts() throws Exception { migrate("112"); }
    @Test void upgradesCurrentV114WithPendingBinaryTreeChapter() throws Exception { migrate("114"); }
    @Test void upgradesCurrentV115WithRemainingTreeTopics() throws Exception { migrate("115"); }

    private void migrate(String initialVersion) throws Exception {
        try (var database = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("learn_platform").withUsername("test").withPassword("test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")) {
            database.start();
            var db = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), "root", database.getPassword()));
            Map<String, List<Map<String, Object>>> before = new LinkedHashMap<>();
            if (initialVersion != null) {
                Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword()).target(initialVersion).load().migrate();
                db.update("""
                        INSERT INTO question_understanding_content
                        (question_id,content_version,review_status,question_fingerprint,knowledge_point_id,
                         solution_explanation,source_title,source_verification)
                        SELECT question_id,2,'PENDING',question_fingerprint,knowledge_point_id,
                          'unrelated solution','unrelated-test-content','PENDING'
                        FROM question_understanding_content WHERE source_title='系统种子生成：合法出栈序列'
                        """);
                db.update("""
                        INSERT INTO question_understanding_attempt(user_id,question_id,content_id,practice_record_id)
                        SELECT 991300,question_id,id,991300 FROM question_understanding_content
                        WHERE source_title='原创 · ll-05'
                        """);
                db.update("""
                        INSERT INTO question_understanding_hint_receipt(attempt_id,hint_id,hint_level,request_id)
                        SELECT a.id,h.id,1,'00000000-0000-4000-8000-000000991300'
                        FROM question_understanding_attempt a JOIN question_understanding_hint h ON h.content_id=a.content_id
                        WHERE a.user_id=991300 AND h.hint_level=1
                        """);
                for (String table : List.of("question_ai_asset", "question_understanding_attempt",
                        "question_understanding_hint_receipt", "question_understanding_viz_view",
                        "question_understanding_variant_attempt", "practice_record", "wrong_question",
                        "question_review_schedule", "question_review_record", "course_learning_event")) {
                    before.put(table, db.queryForList("SELECT * FROM " + table + " ORDER BY id"));
                }
                before.put("question", db.queryForList("SELECT * FROM question WHERE source_reference IS NULL OR source_reference NOT LIKE 'm5-draft:%' ORDER BY id"));
                before.put("question_understanding_content", db.queryForList("SELECT * FROM question_understanding_content WHERE source_title='unrelated-test-content'"));
            }
            var flyway = Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword()).load();
            flyway.migrate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
            for (var entry : before.entrySet()) {
                for (var row : entry.getValue()) {
                    assertEquals(row, db.queryForMap("SELECT * FROM " + entry.getKey() + " WHERE id=?", row.get("id")));
                }
            }
            assertEquals(45, db.queryForObject("SELECT COUNT(*) FROM question_understanding_content WHERE content_version=1", Integer.class));
            assertEquals(10, db.queryForObject("SELECT COUNT(*) FROM question q JOIN question_understanding_content c ON c.question_id=q.id WHERE q.source_reference LIKE 'm5-draft:te-%' AND q.visibility='DRAFT' AND c.review_status='PENDING'", Integer.class));
            assertEquals(8, db.queryForObject("SELECT COUNT(*) FROM question q JOIN question_understanding_content c ON c.question_id=q.id WHERE q.source_reference LIKE 'm5-draft:bt-%' AND q.visibility='DRAFT' AND c.review_status='PENDING'", Integer.class));
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM question_understanding_content WHERE content_version=1 AND source_title IN ('2026 年 408 真题 第 3 题','系统种子生成：合法出栈序列') AND review_status='REVIEWED' AND reviewed_by='AI 初稿 + Codex 冷读 + Claude 页面审核'", Integer.class));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM question_understanding_content WHERE source_title='2026 年 408 真题 第 3 题' AND source_verification='VERIFIED' AND source_verified_by LIKE '%计算机考研杂货铺%' AND source_verified_time IS NOT NULL AND source_url='https://csgraduates.com/study_methods/408quiz/2026/'", Integer.class));
            Path source = Path.of("").toAbsolutePath();
            while (!Files.isDirectory(source.resolve("content/drafts"))) { source = source.getParent(); }
            var json = new ObjectMapper();
            try (var files = Files.list(source.resolve("content/drafts"))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                    for (var q : json.readTree(file.toFile()).get("questions")) {
                        var row = db.queryForMap("SELECT * FROM question WHERE source_reference=?", "m5-draft:" + q.get("id").asText());
                        long id = ((Number) row.get("id")).longValue();
                        assertEquals(q.get("stem").asText(), row.get("content"));
                        assertEquals("AI_GENERATED", row.get("source_type"));
                        assertNull(row.get("owner_user_id"));
                        var c = db.queryForMap("SELECT * FROM question_understanding_content WHERE question_id=?", id);
                        String status = q.get("contentSource").get("reviewStatus").asText();
                        boolean reviewed = "REVIEWED".equals(status);
                        assertEquals(reviewed ? "PUBLIC" : "DRAFT", row.get("visibility"));
                        assertEquals(status, c.get("review_status"));
                        assertEquals("PENDING", c.get("source_verification"));
                        assertEquals("AI_GENERATED", c.get("solution_content_source"));
                        if (reviewed) {
                            assertEquals(q.get("contentSource").get("reviewNote").asText(), c.get("reviewed_by"));
                            assertNotNull(c.get("reviewed_time"));
                        } else { assertNull(c.get("reviewed_by")); assertNull(c.get("reviewed_time")); }
                        assertEquals(q.get("solution").asText(), c.get("solution_explanation"));
                        assertEquals(q.get("solution").asText(), row.get("analysis"));
                        assertEquals(q.path("viz").path("title").isMissingNode() ? null : q.path("viz").path("title").asText(), c.get("solution_viz_title"));
                        assertEquals(q.path("viz").path("spec"), c.get("solution_viz_spec_json") == null
                                ? json.missingNode() : json.readTree(c.get("solution_viz_spec_json").toString()));
                        for (var h : q.get("hints")) {
                            var hint = db.queryForMap("SELECT * FROM question_understanding_hint WHERE content_id=? AND hint_level=?", c.get("id"), h.get("level").asInt());
                            assertEquals(h.get("text").asText(), hint.get("hint_text"));
                            assertEquals(status, hint.get("review_status"));
                            assertEquals("AI_GENERATED", hint.get("content_source"));
                            assertEquals(c.get("reviewed_by"), hint.get("reviewed_by"));
                        }
                        for (var o : q.get("options")) {
                            var option = db.queryForMap("SELECT * FROM question_option WHERE question_id=? AND option_label=?", id, o.get("label").asText());
                            assertEquals(o.get("content").asText(), option.get("content"));
                            assertEquals(o.get("label").asText().equals(q.get("correctAnswer").asText()), ((Number) option.get("is_correct")).intValue() == 1);
                            if (!o.get("label").asText().equals(q.get("correctAnswer").asText())) {
                                var m = db.queryForMap("SELECT * FROM question_understanding_misconception WHERE content_id=? AND option_id=?", c.get("id"), option.get("id"));
                                var expected = q.get("misconceptions").get(o.get("label").asText());
                                assertEquals(expected.get("explanation").asText(), m.get("explanation"));
                                assertEquals(expected.get("title").asText(), m.get("title"));
                                assertEquals("AI_GENERATED", m.get("content_source"));
                            }
                        }
                        var v = db.queryForMap("SELECT v.* FROM ai_variant_question v JOIN question_ai_asset a ON a.id=v.asset_id WHERE a.question_id=?", id);
                        assertEquals(reviewed ? "APPROVED" : "PENDING", v.get("review_status"));
                        if (reviewed) { assertEquals(c.get("reviewed_by"), v.get("review_note")); assertNotNull(v.get("reviewed_time")); }
                        else { assertNull(v.get("reviewed_by")); assertNull(v.get("review_note")); }
                        assertEquals(q.get("variant").get("correctAnswer").asText(), v.get("correct_answer"));
                        assertEquals(q.get("variant").get("explanation").asText(), v.get("analysis"));
                        assertEquals(q.get("variant").get("stem").asText(), v.get("question_content"));
                        assertEquals(q.get("variant").get("options"), json.readTree(v.get("options_json").toString()));
                        assertEquals(q.get("variant").get("changedCondition").asText(), v.get("changed_condition"));
                        assertEquals(q.get("variant").path("viz").path("title").isMissingNode() ? null : q.get("variant").path("viz").path("title").asText(), v.get("viz_title"));
                        assertEquals(q.get("variant").path("viz").path("spec"), v.get("viz_spec_json") == null
                                ? json.missingNode() : json.readTree(v.get("viz_spec_json").toString()));
                    }
                }
            }
        }
    }
}
