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

@Tag("integration")
class QuestionDraftMigrationIntegrationTest {
    @Test void migratesEmptyDatabase() throws Exception { migrate(false); }
    @Test void upgradesCurrentV110AndPreservesExistingRows() throws Exception { migrate(true); }

    private void migrate(boolean upgrade) throws Exception {
        try (var database = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("learn_platform").withUsername("test").withPassword("test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")) {
            database.start();
            var db = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), "root", database.getPassword()));
            Map<String, List<Map<String, Object>>> before = new LinkedHashMap<>();
            if (upgrade) {
                Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword()).target("110").load().migrate();
                for (String table : List.of("question", "question_option", "question_understanding_content", "question_understanding_hint", "ai_variant_question")) {
                    before.put(table, db.queryForList("SELECT * FROM " + table + " ORDER BY id"));
                }
            }
            var flyway = Flyway.configure().dataSource(database.getJdbcUrl(), "root", database.getPassword()).load();
            flyway.migrate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
            for (var entry : before.entrySet()) {
                for (var row : entry.getValue()) {
                    assertEquals(row, db.queryForMap("SELECT * FROM " + entry.getKey() + " WHERE id=?", row.get("id")));
                }
            }
            assertEquals(25, db.queryForObject("SELECT COUNT(*) FROM question WHERE visibility='DRAFT'", Integer.class));
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
                        assertEquals("PENDING", c.get("review_status"));
                        assertEquals("PENDING", c.get("source_verification"));
                        assertEquals("AI_GENERATED", c.get("solution_content_source"));
                        assertNull(c.get("reviewed_by")); assertNull(c.get("reviewed_time"));
                        assertEquals(q.get("solution").asText(), c.get("solution_explanation"));
                        assertEquals(q.path("viz").path("spec"), c.get("solution_viz_spec_json") == null
                                ? json.missingNode() : json.readTree(c.get("solution_viz_spec_json").toString()));
                        for (var h : q.get("hints")) {
                            var hint = db.queryForMap("SELECT * FROM question_understanding_hint WHERE content_id=? AND hint_level=?", c.get("id"), h.get("level").asInt());
                            assertEquals(h.get("text").asText(), hint.get("hint_text"));
                            assertEquals("PENDING", hint.get("review_status"));
                            assertEquals("AI_GENERATED", hint.get("content_source"));
                            assertNull(hint.get("reviewed_by"));
                        }
                        for (var o : q.get("options")) {
                            var option = db.queryForMap("SELECT * FROM question_option WHERE question_id=? AND option_label=?", id, o.get("label").asText());
                            assertEquals(o.get("content").asText(), option.get("content"));
                            if (!o.get("label").asText().equals(q.get("correctAnswer").asText())) {
                                var m = db.queryForMap("SELECT * FROM question_understanding_misconception WHERE content_id=? AND option_id=?", c.get("id"), option.get("id"));
                                var expected = q.get("misconceptions").get(o.get("label").asText());
                                assertEquals(expected.get("explanation").asText(), m.get("explanation"));
                                assertEquals("AI_GENERATED", m.get("content_source"));
                            }
                        }
                        var v = db.queryForMap("SELECT v.* FROM ai_variant_question v JOIN question_ai_asset a ON a.id=v.asset_id WHERE a.question_id=?", id);
                        assertEquals("PENDING", v.get("review_status"));
                        assertNull(v.get("reviewed_by"));
                        assertEquals(q.get("variant").get("correctAnswer").asText(), v.get("correct_answer"));
                        assertEquals(q.get("variant").get("explanation").asText(), v.get("analysis"));
                    }
                }
            }
        }
    }
}
