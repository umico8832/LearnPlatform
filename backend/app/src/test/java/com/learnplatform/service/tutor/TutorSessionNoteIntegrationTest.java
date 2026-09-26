package com.learnplatform.service.tutor;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.TutorSessionNoteUpdateRequest;
import com.learnplatform.dto.TutorSessionNoteVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-MySQL coverage for explicit session notes and their live Tutor-check evidence. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class TutorSessionNoteIntegrationTest extends IntegrationTestBase {
    private static final long USER = 989501L;
    private static final long OTHER = 989502L;
    private static final long COURSE = 989510L;
    private static final long OTHER_COURSE = 989511L;
    private static final long POINT = 989520L;
    private static final long CONTENT = 989530L;
    private static final String SESSION = "00000000-0000-0000-0000-000000989501";

    @Autowired private TutorSessionNoteService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach void prepare() {
        clean();
        jdbc.update("INSERT INTO course (id,name,status,deleted) VALUES (?,'会话复盘课程',1,0),(?,'其他课程',1,0)",
                COURSE, OTHER_COURSE);
        jdbc.update("INSERT INTO user_course (user_id,course_id) VALUES (?,?),(?,?)", USER, COURSE, OTHER, COURSE);
        session(SESSION, USER, COURSE, POINT, CONTENT, "复盘知识点", "REVIEWED", "REVIEWED", "复盘课节");
    }

    @AfterEach void clean() {
        if (jdbc == null) return;
        jdbc.update("DELETE FROM tutor_session_note WHERE session_id IN "
                + "(SELECT id FROM tutor_session WHERE course_id IN (?,?))", COURSE, OTHER_COURSE);
        jdbc.update("DELETE FROM course_learning_event WHERE user_id IN (?,?)", USER, OTHER);
        jdbc.update("DELETE FROM tutor_session WHERE course_id IN (?,?)", COURSE, OTHER_COURSE);
        jdbc.update("DELETE FROM tutor_content WHERE id BETWEEN ? AND ?", CONTENT, CONTENT + 99);
        jdbc.update("DELETE FROM knowledge_point WHERE id BETWEEN ? AND ?", POINT, POINT + 99);
        jdbc.update("DELETE FROM user_course WHERE course_id IN (?,?)", COURSE, OTHER_COURSE);
        jdbc.update("DELETE FROM course WHERE id IN (?,?)", COURSE, OTHER_COURSE);
    }

    @Test void savesUserTextAndReadsCurrentCheckEvidenceWithoutWritingLearningFacts() {
        TutorSessionNoteVO saved = service.save(USER, COURSE, SESSION,
                new TutorSessionNoteUpdateRequest(0L, "  先复盘右移方向  "));
        assertEquals(1L, saved.revision());
        assertEquals("先复盘右移方向", saved.note());
        assertTrue(saved.source().available());
        assertEquals(POINT, saved.source().knowledgePointId());
        assertEquals("复盘课节", saved.source().title());
        assertEquals("UNANSWERED", saved.source().checkStatus());
        assertNull(saved.source().checkAnsweredAt());
        assertTrue(saved.updatedAt() != null);

        LocalDateTime answeredAt = LocalDateTime.of(2026, 9, 26, 12, 30, 15);
        jdbc.update("UPDATE tutor_session SET check_answer='A', check_correct=1, check_answered_at=? WHERE session_key=?",
                answeredAt, SESSION);
        TutorSessionNoteVO reloaded = service.get(USER, COURSE, SESSION);
        assertEquals("CORRECT", reloaded.source().checkStatus());
        assertEquals(answeredAt, reloaded.source().checkAnsweredAt());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM course_learning_event WHERE user_id=?", Integer.class, USER));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM practice_record WHERE user_id=?", Integer.class, USER));
    }

    @Test void correctionAndDeletionKeepTombstoneAndRejectStaleResurrection() {
        service.save(USER, COURSE, SESSION, new TutorSessionNoteUpdateRequest(0L, "初稿"));
        TutorSessionNoteVO corrected = service.save(USER, COURSE, SESSION,
                new TutorSessionNoteUpdateRequest(1L, "修正后的复盘"));
        assertEquals(2L, corrected.revision());
        TutorSessionNoteVO erased = service.delete(USER, COURSE, SESSION, 2L);
        assertEquals(3L, erased.revision());
        assertNull(erased.note());
        assertEquals(3L, jdbc.queryForObject("SELECT revision FROM tutor_session_note WHERE session_id="
                + "(SELECT id FROM tutor_session WHERE session_key=?)", Long.class, SESSION));
        assertNull(jdbc.queryForObject("SELECT note FROM tutor_session_note WHERE session_id="
                + "(SELECT id FROM tutor_session WHERE session_key=?)", String.class, SESSION));
        assertTrue(service.list(USER, COURSE, 1).records().isEmpty());
        assertTrue(service.forPrompt(USER, COURSE).isEmpty());
        for (long stale : List.of(0L, 1L, 2L)) {
            assertEquals(ResultCode.BUSINESS_ERROR.getCode(), assertThrows(BusinessException.class,
                    () -> service.save(USER, COURSE, SESSION, new TutorSessionNoteUpdateRequest(stale, "旧页面"))).getCode());
        }
        assertEquals(4L, service.save(USER, COURSE, SESSION,
                new TutorSessionNoteUpdateRequest(3L, "新的复盘")).revision());
    }

    @Test void isolatesOwnersAndRequiresCurrentMembershipAndCourseAvailability() {
        service.save(USER, COURSE, SESSION, new TutorSessionNoteUpdateRequest(0L, "我的复盘"));
        assertEquals(ResultCode.NOT_FOUND.getCode(), assertThrows(BusinessException.class,
                () -> service.get(OTHER, COURSE, SESSION)).getCode());
        assertThrows(BusinessException.class, () -> service.get(USER, OTHER_COURSE, SESSION));
        jdbc.update("DELETE FROM user_course WHERE user_id=? AND course_id=?", USER, COURSE);
        assertThrows(BusinessException.class, () -> service.get(USER, COURSE, SESSION));
        assertThrows(BusinessException.class, () -> service.delete(USER, COURSE, SESSION, 1L));
        jdbc.update("INSERT INTO user_course (user_id,course_id) VALUES (?,?)", USER, COURSE);
        jdbc.update("UPDATE course SET status=0 WHERE id=?", COURSE);
        assertThrows(BusinessException.class, () -> service.get(USER, COURSE, SESSION));
    }

    @Test void withdrawnContentOrKnowledgePointHidesEvidenceButKeepsSelfWrittenTextDeletable() {
        service.save(USER, COURSE, SESSION, new TutorSessionNoteUpdateRequest(0L, "撤回后仍需删除的笔记"));
        jdbc.update("UPDATE tutor_content SET review_status='REVIEW_PENDING' WHERE id=?", CONTENT);
        TutorSessionNoteVO withdrawnContent = service.get(USER, COURSE, SESSION);
        assertEquals("撤回后仍需删除的笔记", withdrawnContent.note());
        assertFalse(withdrawnContent.source().available());
        assertNull(withdrawnContent.source().title());
        assertNull(withdrawnContent.source().sessionStartedAt());
        assertNull(withdrawnContent.source().checkStatus());
        assertThrows(BusinessException.class, () -> service.save(USER, COURSE, SESSION,
                new TutorSessionNoteUpdateRequest(1L, "不能纠正到已撤回来源")));
        assertEquals(2L, service.delete(USER, COURSE, SESSION, 1L).revision());

        jdbc.update("UPDATE tutor_content SET review_status='REVIEWED' WHERE id=?", CONTENT);
        service.save(USER, COURSE, SESSION, new TutorSessionNoteUpdateRequest(2L, "再次保存"));
        jdbc.update("UPDATE knowledge_point SET content_review_status='REVIEW_PENDING' WHERE id=?", POINT);
        TutorSessionNoteVO withdrawnPoint = service.get(USER, COURSE, SESSION);
        assertFalse(withdrawnPoint.source().available());
        assertNull(withdrawnPoint.source().knowledgePointId());
        assertNull(withdrawnPoint.source().checkAnsweredAt());
    }

    @Test void sameRevisionDeleteAndCorrectionHaveOneWinner() throws Exception {
        service.save(USER, COURSE, SESSION, new TutorSessionNoteUpdateRequest(0L, "初始复盘"));
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> correct = () -> replaceAtRevision(start, "纠正后的复盘", false);
        Callable<Boolean> delete = () -> replaceAtRevision(start, null, true);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var correction = executor.submit(correct);
            var deletion = executor.submit(delete);
            start.countDown();
            assertEquals(1, (correction.get() ? 1 : 0) + (deletion.get() ? 1 : 0));
        }
        TutorSessionNoteVO current = service.get(USER, COURSE, SESSION);
        assertEquals(2L, current.revision());
        assertTrue(current.note() == null || "纠正后的复盘".equals(current.note()));
    }

    @Test void concurrentInitialSavesHaveOneWinnerAndKeepOneRevision() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> save = () -> {
            start.await();
            try {
                service.save(USER, COURSE, SESSION, new TutorSessionNoteUpdateRequest(0L, Thread.currentThread().getName()));
                return true;
            } catch (BusinessException exception) {
                assertEquals(ResultCode.BUSINESS_ERROR.getCode(), exception.getCode());
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(save);
            var second = executor.submit(save);
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
        assertEquals(1L, service.get(USER, COURSE, SESSION).revision());
    }

    @Test void paginationIncludesOwnWithdrawnNotesWhilePromptTakesFiveAvailableNotesBeforeLimiting() {
        service.save(USER, COURSE, SESSION, new TutorSessionNoteUpdateRequest(0L, "基础笔记"));
        for (int index = 1; index <= 7; index++) {
            String key = String.format("00000000-0000-0000-0000-%012d", 989501 + index);
            long point = POINT + index;
            long content = CONTENT + index;
            session(key, USER, COURSE, point, content, "复盘点" + index, "REVIEWED", "REVIEWED", "课节" + index);
            service.save(USER, COURSE, key, new TutorSessionNoteUpdateRequest(0L, "笔记" + index));
        }
        // The latest note is withdrawn. Prompt selection must filter it before applying the five-note limit.
        jdbc.update("UPDATE tutor_content SET review_status='REVIEW_PENDING' WHERE id=?", CONTENT + 7);

        var first = service.list(USER, COURSE, 1);
        var second = service.list(USER, COURSE, 2);
        assertEquals(8L, first.total());
        assertEquals(5, first.size());
        assertEquals(5, first.records().size());
        assertEquals(3, second.records().size());
        assertTrue(first.records().stream().anyMatch(note -> !note.source().available()));

        List<TutorSessionNoteVO> prompt = service.forPrompt(USER, COURSE);
        assertEquals(5, prompt.size());
        assertTrue(prompt.stream().allMatch(note -> note.source().available()));
        assertFalse(prompt.stream().anyMatch(note -> keyFor(CONTENT + 7).equals(note.sessionKey())));
        assertTrue(prompt.stream().anyMatch(note -> keyFor(CONTENT + 2).equals(note.sessionKey())));
    }

    @Test void rejectsBlankOrOversizedNotesWithoutCreatingRows() {
        for (String note : List.of("   ", "长".repeat(501))) {
            assertEquals(ResultCode.VALIDATION_ERROR.getCode(), assertThrows(BusinessException.class,
                    () -> service.save(USER, COURSE, SESSION, new TutorSessionNoteUpdateRequest(0L, note))).getCode());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tutor_session_note", Integer.class));
        assertNull(service.get(USER, COURSE, SESSION).note());
    }

    private void session(String key, long user, long course, long point, long content, String pointName,
                         String pointStatus, String contentStatus, String title) {
        jdbc.update("INSERT INTO knowledge_point (id,name,course_id,content_review_status) VALUES (?,?,?,?)",
                point, pointName, course, pointStatus);
        jdbc.update("INSERT INTO tutor_content (id,knowledge_point_id,content_key,content_version,review_status,title,lesson_json,check_json) "
                        + "VALUES (?,?,?,1,?,?, '{}','{}')",
                content, point, "session-note-" + content, contentStatus, title);
        jdbc.update("INSERT INTO tutor_session (session_key,user_id,course_id,knowledge_point_id,tutor_content_id,learning_context_json) "
                        + "VALUES (?,?,?,?,?, '{}')", key, user, course, point, content);
    }

    private String keyFor(long content) {
        int index = Math.toIntExact(content - CONTENT);
        return String.format("00000000-0000-0000-0000-%012d", 989501 + index);
    }

    private boolean replaceAtRevision(CountDownLatch start, String note, boolean delete) throws Exception {
        start.await();
        try {
            if (delete) {
                service.delete(USER, COURSE, SESSION, 1L);
            } else {
                service.save(USER, COURSE, SESSION, new TutorSessionNoteUpdateRequest(1L, note));
            }
            return true;
        } catch (BusinessException exception) {
            assertEquals(ResultCode.BUSINESS_ERROR.getCode(), exception.getCode());
            return false;
        }
    }
}
