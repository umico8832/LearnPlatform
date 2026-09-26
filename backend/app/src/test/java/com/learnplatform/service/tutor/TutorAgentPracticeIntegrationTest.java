package com.learnplatform.service.tutor;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.TutorAgentPracticeAnswerRequest;
import com.learnplatform.dto.TutorAgentPracticeVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Real MySQL coverage for the published Tutor practice boundary and first-answer transaction. */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class TutorAgentPracticeIntegrationTest extends IntegrationTestBase {
    private static final long USER = 989101L;
    private static final long OTHER_USER = 989102L;
    private static final long COURSE = 989110L;
    private static final long OTHER_COURSE = 989111L;
    private static final long POINT = 989120L;
    private static final long CONTENT = 989130L;
    private static final long MOTHER = 989140L;
    private static final long PUBLISHED = 989141L;
    private static final long ASSET = 989150L;
    private static final long VARIANT = 989160L;
    private static final String SESSION = "00000000-0000-0000-0000-000000989101";
    private static final String RUN = "00000000-0000-0000-0000-000000989102";
    private static final String FOREIGN_SESSION = "00000000-0000-0000-0000-000000989103";
    private static final String FOREIGN_OWNER_RUN = "00000000-0000-0000-0000-000000989104";
    private static final String FOREIGN_SESSION_RUN = "00000000-0000-0000-0000-000000989105";

    @Autowired private TutorAgentPracticeService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("INSERT INTO user (id,username,password,role,status,deleted) VALUES (?,?,'test','USER',1,0), (?,?,'test','USER',1,0)",
                USER, "tutor-practice-owner", OTHER_USER, "tutor-practice-other");
        jdbc.update("INSERT INTO course (id,name,status,deleted) VALUES (?,'Tutor 练习课程',1,0), (?,'其他课程',1,0)", COURSE, OTHER_COURSE);
        jdbc.update("INSERT INTO user_course (user_id,course_id) VALUES (?,?)", USER, COURSE);
        jdbc.update("INSERT INTO knowledge_point (id,name,course_id,content_review_status) VALUES (?,'Tutor 练习点',?,'REVIEWED')", POINT, COURSE);
        jdbc.update("""
                INSERT INTO tutor_content (id,knowledge_point_id,content_key,content_version,review_status,title,lesson_json,check_json)
                VALUES (?,?,'tutor-practice-integration',1,'REVIEWED','Tutor 练习','{}','{}')
                """, CONTENT, POINT);
        jdbc.update("""
                INSERT INTO tutor_session (session_key,user_id,course_id,knowledge_point_id,tutor_content_id,learning_context_json)
                VALUES (?,?,?,?,?, '{}')
                """, SESSION, USER, COURSE, POINT, CONTENT);
        insertQuestions();
        jdbc.update("INSERT INTO question_ai_asset (id,question_id,asset_type,content,model,deleted) VALUES (?,?,'VARIANT','{}','test',0)", ASSET, MOTHER);
        jdbc.update("""
                INSERT INTO ai_variant_question (id,asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,
                  review_status,published_question_id) VALUES (?,?,'SINGLE_CHOICE','变式','[]','A','解析',3,'APPROVED',?)
                """, VARIANT, ASSET, PUBLISHED);
        jdbc.update("INSERT INTO tutor_agent_run (run_key,tutor_session_id,user_id,status,next_sequence) VALUES (?,(SELECT id FROM tutor_session WHERE session_key=?),?,'WAITING_USER',3)", RUN, SESSION, USER);
        jdbc.update("""
                INSERT INTO tutor_agent_message (run_id,sequence_no,role,content,actions_json)
                VALUES ((SELECT id FROM tutor_agent_run WHERE run_key=?),2,'ASSISTANT','请作答','[{"type":"PRACTICE","questionId":989141}]')
                """, RUN);
        jdbc.update("""
                INSERT INTO tutor_session (session_key,user_id,course_id,knowledge_point_id,tutor_content_id,learning_context_json)
                VALUES (?,?,?,?,?, '{}')
                """, FOREIGN_SESSION, USER, COURSE, POINT, CONTENT);
        jdbc.update("INSERT INTO tutor_agent_run (run_key,tutor_session_id,user_id,status,next_sequence) VALUES (?,(SELECT id FROM tutor_session WHERE session_key=?),?,'WAITING_USER',3)",
                FOREIGN_OWNER_RUN, SESSION, OTHER_USER);
        jdbc.update("INSERT INTO tutor_agent_run (run_key,tutor_session_id,user_id,status,next_sequence) VALUES (?,(SELECT id FROM tutor_session WHERE session_key=?),?,'WAITING_USER',3)",
                FOREIGN_SESSION_RUN, FOREIGN_SESSION, USER);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void recommendsOnlyEligiblePublishedQuestionWithoutWritingLearningFacts() {
        assertEquals(PUBLISHED, service.recommend(USER, COURSE, SESSION));
        assertEquals(0, count("SELECT COUNT(*) FROM tutor_agent_practice_attempt"));
        assertEquals(0, count("SELECT COUNT(*) FROM practice_record WHERE user_id=?", USER));

        jdbc.update("UPDATE question SET visibility='PRIVATE' WHERE id=?", MOTHER);
        assertNull(service.recommend(USER, COURSE, SESSION));
        jdbc.update("UPDATE question SET visibility='PUBLIC' WHERE id=?", MOTHER);
        jdbc.update("UPDATE ai_variant_question SET review_status='PENDING' WHERE id=?", VARIANT);
        assertNull(service.recommend(USER, COURSE, SESSION));
    }

    @Test
    void getAndLatestResultRejectForgedRunAndRecheckEligibility() {
        assertThrows(BusinessException.class, () -> service.get(OTHER_USER, COURSE, SESSION, RUN, 2));
        assertThrows(BusinessException.class, () -> service.get(USER, OTHER_COURSE, SESSION, RUN, 2));
        assertThrows(BusinessException.class, () -> service.get(USER, COURSE, SESSION, FOREIGN_OWNER_RUN, 2));
        assertThrows(BusinessException.class, () -> service.get(USER, COURSE, SESSION, FOREIGN_SESSION_RUN, 2));
        assertThrows(BusinessException.class, () -> service.latestResult(USER, COURSE, SESSION,
                "00000000-0000-0000-0000-000000989199"));
        jdbc.update("UPDATE tutor_content SET review_status='REVIEW_PENDING' WHERE id=?", CONTENT);
        assertThrows(BusinessException.class, () -> service.get(USER, COURSE, SESSION, RUN, 2));
        jdbc.update("UPDATE tutor_content SET review_status='REVIEWED' WHERE id=?", CONTENT);
        jdbc.update("UPDATE question SET status=0 WHERE id=?", PUBLISHED);
        assertThrows(BusinessException.class, () -> service.get(USER, COURSE, SESSION, RUN, 2));
        assertThrows(BusinessException.class, () -> service.latestResult(USER, COURSE, SESSION, RUN));
    }

    @Test
    void concurrentFirstAnswersPersistOneRecordEventAndAttempt() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<TutorAgentPracticeVO> first = () -> { start.await(); return answer("A"); };
        Callable<TutorAgentPracticeVO> second = () -> { start.await(); return answer("B"); };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<TutorAgentPracticeVO>> answers = List.of(executor.submit(first), executor.submit(second));
            start.countDown();
            assertEquals(answers.get(0).get().result().getRecordId(), answers.get(1).get().result().getRecordId());
        }
        assertEquals(1, count("SELECT COUNT(*) FROM tutor_agent_practice_attempt"));
        assertEquals(1, count("SELECT COUNT(*) FROM practice_record WHERE user_id=? AND question_id=?", USER, PUBLISHED));
        assertEquals(1, count("SELECT COUNT(*) FROM course_learning_event WHERE user_id=? AND course_id=? AND event_source='PRACTICE'", USER, COURSE));
    }

    @Test
    void rejectsInvalidOptionWithoutCreatingLearningFacts() {
        assertThrows(BusinessException.class, () -> answer("Z"));
        assertEquals(0, count("SELECT COUNT(*) FROM tutor_agent_practice_attempt"));
        assertEquals(0, count("SELECT COUNT(*) FROM practice_record WHERE user_id=?", USER));
        assertEquals(0, count("SELECT COUNT(*) FROM course_learning_event WHERE user_id=? AND course_id=?", USER, COURSE));
    }

    @Test
    void latestResultUsesLatestPracticeActionEvenAfterNormalAssistantMessages() {
        long recordId = answer("A").result().getRecordId();
        jdbc.update("""
                INSERT INTO tutor_agent_message (run_id,sequence_no,role,content,actions_json)
                VALUES ((SELECT id FROM tutor_agent_run WHERE run_key=?),3,'ASSISTANT','继续学习',NULL)
                """, RUN);
        assertEquals(recordId, service.latestResult(USER, COURSE, SESSION, RUN).getRecordId());
        jdbc.update("""
                INSERT INTO tutor_agent_message (run_id,sequence_no,role,content,actions_json)
                VALUES ((SELECT id FROM tutor_agent_run WHERE run_key=?),4,'ASSISTANT','下一题','[{"type":"PRACTICE","questionId":989141}]')
                """, RUN);
        assertNull(service.latestResult(USER, COURSE, SESSION, RUN));
    }

    @Test
    void restoresTheOriginalQuestionSnapshotAfterPublishedQuestionChanges() {
        TutorAgentPracticeVO answered = answer("A");
        jdbc.update("UPDATE question SET content='修改后的题干' WHERE id=?", PUBLISHED);
        jdbc.update("UPDATE question_option SET content='修改后的正确项' WHERE question_id=? AND option_label='A'", PUBLISHED);

        TutorAgentPracticeVO restored = service.get(USER, COURSE, SESSION, RUN, 2);
        assertEquals("发布题", restored.question().content());
        assertEquals("正确项", restored.question().options().getFirst().content());
        assertEquals(answered.result().getRecordId(), restored.result().getRecordId());
        assertEquals(answered.result().getCorrectAnswer(), restored.result().getCorrectAnswer());
        assertEquals(restored.question(), answer("B").question());
    }

    @Test
    void rollsBackPracticeRecordWhenAttemptSnapshotCannotBeInserted() {
        jdbc.execute("ALTER TABLE tutor_agent_practice_attempt MODIFY result_json VARCHAR(1) NOT NULL");
        try {
            assertThrows(Exception.class, () -> answer("A"));
            assertEquals(0, count("SELECT COUNT(*) FROM practice_record WHERE user_id=? AND question_id=?", USER, PUBLISHED));
            assertEquals(0, count("SELECT COUNT(*) FROM course_learning_event WHERE user_id=? AND course_id=?", USER, COURSE));
        } finally {
            jdbc.execute("ALTER TABLE tutor_agent_practice_attempt MODIFY result_json JSON NOT NULL");
        }
    }

    private TutorAgentPracticeVO answer(String option) {
        TutorAgentPracticeAnswerRequest request = new TutorAgentPracticeAnswerRequest();
        request.setUserAnswer(option);
        return service.answer(USER, COURSE, SESSION, RUN, 2, request);
    }

    private void insertQuestions() {
        jdbc.update("""
                INSERT INTO question (id,content,question_type,course_id,difficulty,score,status,visibility,deleted)
                VALUES (?, '母题', 'SINGLE_CHOICE', ?, 3, 1, 1, 'PUBLIC', 0), (?, '发布题', 'SINGLE_CHOICE', ?, 3, 1, 1, 'PUBLIC', 0)
                """, MOTHER, COURSE, PUBLISHED, COURSE);
        jdbc.update("INSERT INTO question_knowledge_point (question_id,knowledge_point_id) VALUES (?,?),(?,?)", MOTHER, POINT, PUBLISHED, POINT);
        jdbc.update("""
                INSERT INTO question_option (question_id,content,option_label,is_correct,sort_order,deleted)
                VALUES (?, '正确项', 'A', 1, 1, 0), (?, '错误项', 'B', 0, 2, 0)
                """, PUBLISHED, PUBLISHED);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private void cleanUp() {
        if (jdbc == null) return;
        jdbc.update("DELETE FROM tutor_agent_practice_attempt WHERE message_id IN (SELECT id FROM tutor_agent_message WHERE run_id IN (SELECT id FROM tutor_agent_run WHERE run_key IN (?,?,?)))",
                RUN, FOREIGN_OWNER_RUN, FOREIGN_SESSION_RUN);
        jdbc.update("DELETE FROM course_learning_event WHERE user_id IN (?,?)", USER, OTHER_USER);
        jdbc.update("DELETE FROM practice_record WHERE user_id IN (?,?)", USER, OTHER_USER);
        jdbc.update("DELETE FROM wrong_question WHERE user_id IN (?,?)", USER, OTHER_USER);
        jdbc.update("DELETE FROM question_review_schedule WHERE user_id IN (?,?)", USER, OTHER_USER);
        jdbc.update("DELETE FROM tutor_agent_message WHERE run_id IN (SELECT id FROM tutor_agent_run WHERE run_key IN (?,?,?))",
                RUN, FOREIGN_OWNER_RUN, FOREIGN_SESSION_RUN);
        jdbc.update("DELETE FROM tutor_agent_run WHERE run_key IN (?,?,?)", RUN, FOREIGN_OWNER_RUN, FOREIGN_SESSION_RUN);
        jdbc.update("DELETE FROM tutor_session WHERE session_key IN (?,?)", SESSION, FOREIGN_SESSION);
        jdbc.update("DELETE FROM ai_variant_question WHERE id=?", VARIANT);
        jdbc.update("DELETE FROM question_ai_asset WHERE id=?", ASSET);
        jdbc.update("DELETE FROM question_option WHERE question_id IN (?,?)", MOTHER, PUBLISHED);
        jdbc.update("DELETE FROM question_knowledge_point WHERE question_id IN (?,?)", MOTHER, PUBLISHED);
        jdbc.update("DELETE FROM question WHERE id IN (?,?)", MOTHER, PUBLISHED);
        jdbc.update("DELETE FROM tutor_content WHERE id=?", CONTENT);
        jdbc.update("DELETE FROM knowledge_point WHERE id=?", POINT);
        jdbc.update("DELETE FROM user_course WHERE user_id IN (?,?)", USER, OTHER_USER);
        jdbc.update("DELETE FROM course WHERE id IN (?,?)", COURSE, OTHER_COURSE);
        jdbc.update("DELETE FROM user WHERE id IN (?,?)", USER, OTHER_USER);
    }
}
