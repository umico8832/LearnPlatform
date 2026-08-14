package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class PrivateExamContentLifecycleIntegrationTest extends IntegrationTestBase {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PrivateExamContentLifecycleService service;

    @Test
    void deletesUnconfirmedDraftAndItsUnreferencedSource() {
        Long userId = userId();
        Long sourceId = insertSource(userId, "draft-source");
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT OCTET_LENGTH(source_file) FROM user_exam_source WHERE id = ?", Long.class, sourceId) > 0);
        jdbcTemplate.update("""
                INSERT INTO private_exam_import_draft
                  (owner_user_id,title,course_id,duration,source_record_id,status)
                VALUES (?, '待删除草稿', ?, 60, ?, 'REVIEWING')
                """, userId, courseId(), sourceId);
        Long draftId = lastId();
        jdbcTemplate.update("""
                INSERT INTO private_exam_draft_question
                  (draft_id,sort_order,content,question_type,score,options_json,generation_status,review_status)
                VALUES (?,1,'待删除题','SINGLE_CHOICE',1,'[{"label":"A","content":"是"},{"label":"B","content":"否"}]','PENDING','PENDING')
                """, draftId);

        service.deleteDraft(draftId, userId);

        assertEquals(0, count("private_exam_import_draft", draftId));
        assertEquals(0, count("user_exam_source", sourceId));
    }

    @Test
    void physicallyDeletesUnusedPrivatePaperAndDecomposedQuestions() {
        PrivatePaperFixture fixture = insertPrivatePaper("unused-paper");

        service.deletePaper(fixture.paperId(), fixture.userId());

        assertEquals(0, count("exam_paper", fixture.paperId()));
        assertEquals(0, count("question", fixture.questionId()));
        assertEquals(0, count("private_exam_import_draft", fixture.draftId()));
        assertEquals(0, count("user_exam_source", fixture.sourceId()));
    }

    @Test
    void keepsPrivatePaperWhenExamRecordExists() {
        PrivatePaperFixture fixture = insertPrivatePaper("referenced-paper");
        jdbcTemplate.update("""
                INSERT INTO exam_record (user_id,exam_paper_id,start_time,total_score,status)
                VALUES (?,?,NOW(),1,0)
                """, fixture.userId(), fixture.paperId());

        assertThrows(BusinessException.class, () -> service.deletePaper(fixture.paperId(), fixture.userId()));

        assertEquals(1, count("exam_paper", fixture.paperId()));
        assertEquals(1, count("question", fixture.questionId()));
    }

    private PrivatePaperFixture insertPrivatePaper(String suffix) {
        Long userId = userId();
        Long sourceId = insertSource(userId, suffix);
        jdbcTemplate.update("""
                INSERT INTO exam_paper
                  (title,course_id,total_score,duration,question_count,status,create_by,owner_user_id,
                   visibility,paper_type,source_reference,source_verified,source_record_id,import_status,deleted)
                VALUES (?, ?, 1, 60, 1, 1, ?, ?, 'PRIVATE', 'USER_PRIVATE', ?, 0, ?, 'CONFIRMED', 0)
                """, "测试私有试卷-" + suffix, courseId(), userId, userId, "user-source:" + suffix, sourceId);
        Long paperId = lastId();
        jdbcTemplate.update("""
                INSERT INTO question
                  (content,question_type,course_id,difficulty,analysis,score,status,create_by,owner_user_id,
                   visibility,source_type,source_reference,review_rounds,deleted)
                VALUES ('待删除私有题','SINGLE_CHOICE',?,3,'解析',1,1,?,?,'PRIVATE','USER_PRIVATE_IMPORT',?,0,0)
                """, courseId(), userId, userId, "user-source:" + sourceId);
        Long questionId = lastId();
        jdbcTemplate.update("""
                INSERT INTO question_option (question_id,content,option_label,is_correct,sort_order,deleted)
                VALUES (?,'是','A',1,1,0),(?,'否','B',0,2,0)
                """, questionId, questionId);
        jdbcTemplate.update("""
                INSERT INTO exam_question (exam_paper_id,question_id,sort_order,score,display_number)
                VALUES (?,?,1,1,'1')
                """, paperId, questionId);
        jdbcTemplate.update("""
                INSERT INTO private_exam_import_draft
                  (owner_user_id,title,course_id,duration,source_record_id,status,confirmed_paper_id)
                VALUES (?, ?, ?, 60, ?, 'CONFIRMED', ?)
                """, userId, "测试私有试卷-" + suffix, courseId(), sourceId, paperId);
        Long draftId = lastId();
        jdbcTemplate.update("""
                INSERT INTO private_exam_draft_question
                  (draft_id,sort_order,content,question_type,score,options_json,generation_status,review_status)
                VALUES (?,1,'已确认题','SINGLE_CHOICE',1,'[{"label":"A","content":"是"}]','READY','CONFIRMED')
                """, draftId);
        return new PrivatePaperFixture(userId, sourceId, paperId, questionId, draftId);
    }

    private Long insertSource(Long userId, String suffix) {
        byte[] sourceFile = ("%PDF-" + suffix).getBytes();
        jdbcTemplate.update("""
                INSERT INTO user_exam_source
                  (owner_user_id,source_name,source_format,content_sha256,original_content,
                   source_media_type,source_size,source_file)
                VALUES (?,?,'PDF',?,'原始资料','application/pdf',?,?)
                """, userId, suffix + ".pdf", sha256(sourceFile), sourceFile.length, sourceFile);
        return lastId();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Long userId() {
        return jdbcTemplate.queryForObject("SELECT id FROM user WHERE username='testuser'", Long.class);
    }

    private Long courseId() {
        return jdbcTemplate.queryForObject("SELECT id FROM course WHERE content_key='cs408-data-structures'", Long.class);
    }

    private Long lastId() {
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Integer count(String table, Long id) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    private record PrivatePaperFixture(Long userId, Long sourceId, Long paperId, Long questionId, Long draftId) { }
}
