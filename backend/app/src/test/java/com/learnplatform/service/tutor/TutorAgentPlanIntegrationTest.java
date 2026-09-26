package com.learnplatform.service.tutor;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.TutorAgentPlanVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class TutorAgentPlanIntegrationTest extends IntegrationTestBase {
    private static final long USER = 989201L;
    private static final long OTHER_USER = 989202L;
    private static final long COURSE = 989210L;
    private static final long POINT = 989220L;
    private static final long CONTENT = 989230L;
    private static final String SESSION = "00000000-0000-0000-0000-000000989201";
    private static final String RUN = "00000000-0000-0000-0000-000000989202";

    @Autowired private TutorAgentPlanService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("INSERT INTO user (id,username,password,role,status,deleted) VALUES (?,?,'test','USER',1,0),(?,?,'test','USER',1,0)",
                USER, "tutor-plan-owner", OTHER_USER, "tutor-plan-other");
        jdbc.update("INSERT INTO course (id,name,status,deleted) VALUES (?,'Tutor 计划课程',1,0)", COURSE);
        jdbc.update("INSERT INTO user_course (user_id,course_id) VALUES (?,?)", USER, COURSE);
        jdbc.update("INSERT INTO knowledge_point (id,name,course_id,parent_id,content_review_status,deleted) VALUES (?,'计划起点',?,0,'REVIEWED',0)",
                POINT, COURSE);
        jdbc.update("""
                INSERT INTO tutor_content (id,knowledge_point_id,content_key,content_version,review_status,title,lesson_json,check_json)
                VALUES (?,?,'tutor-plan-integration',1,'REVIEWED','计划教学','{}','{}')
                """, CONTENT, POINT);
        jdbc.update("""
                INSERT INTO tutor_session (session_key,user_id,course_id,knowledge_point_id,tutor_content_id,learning_context_json)
                VALUES (?,?,?,?,?, '{}')
                """, SESSION, USER, COURSE, POINT, CONTENT);
        jdbc.update("INSERT INTO tutor_agent_run (run_key,tutor_session_id,user_id,status,next_sequence) VALUES (?,(SELECT id FROM tutor_session WHERE session_key=?),?,'WAITING_USER',3)",
                RUN, SESSION, USER);
        jdbc.update("""
                INSERT INTO tutor_agent_message (run_id,sequence_no,role,content,actions_json)
                VALUES ((SELECT id FROM tutor_agent_run WHERE run_key=?),2,'ASSISTANT','计划建议',
                  '[{"type":"PLAN","steps":[{"type":"TUTOR","title":"继续教学","reason":"继续","knowledgePointId":989220}]}]')
                """, RUN);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void confirmsOnceWithoutWritingLearningFactsAndRestoresConfirmationState() {
        TutorAgentPlanVO confirmed = service.confirm(USER, COURSE, SESSION, RUN, 2);
        assertTrue(confirmed.confirmed());
        assertTrue(confirmed.available());
        assertNotNull(confirmed.confirmedAt());
        assertEquals(1, count("SELECT COUNT(*) FROM tutor_agent_plan_confirmation"));
        assertEquals(0, count("SELECT COUNT(*) FROM course_learning_event WHERE user_id=?", USER));
        assertEquals(0, count("SELECT COUNT(*) FROM practice_record WHERE user_id=?", USER));

        TutorAgentPlanVO restored = service.get(USER, COURSE, SESSION, RUN, 2);
        assertEquals(confirmed.confirmedAt(), restored.confirmedAt());
        assertTrue(restored.confirmed());
    }

    @Test
    void concurrentConfirmationsReturnOneStoredTimestamp() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<TutorAgentPlanVO> confirm = () -> { start.await(); return service.confirm(USER, COURSE, SESSION, RUN, 2); };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<TutorAgentPlanVO>> confirmations = List.of(executor.submit(confirm), executor.submit(confirm));
            start.countDown();
            assertEquals(confirmations.get(0).get().confirmedAt(), confirmations.get(1).get().confirmedAt());
        }
        assertEquals(1, count("SELECT COUNT(*) FROM tutor_agent_plan_confirmation"));
    }

    @Test
    void rejectsForeignRunAndUnavailableNewConfirmationButReadsConfirmedStalePlan() {
        assertThrows(BusinessException.class, () -> service.get(OTHER_USER, COURSE, SESSION, RUN, 2));
        jdbc.update("UPDATE knowledge_point SET deleted=1 WHERE id=?", POINT);
        assertThrows(BusinessException.class, () -> service.confirm(USER, COURSE, SESSION, RUN, 2));

        jdbc.update("UPDATE knowledge_point SET deleted=0 WHERE id=?", POINT);
        TutorAgentPlanVO confirmed = service.confirm(USER, COURSE, SESSION, RUN, 2);
        jdbc.update("UPDATE knowledge_point SET deleted=1 WHERE id=?", POINT);
        TutorAgentPlanVO stale = service.get(USER, COURSE, SESSION, RUN, 2);
        assertTrue(confirmed.confirmed());
        assertTrue(stale.confirmed());
        assertFalse(stale.available());
    }

    @Test
    void latestFindsPlanBeforeLaterOrdinaryAssistantMessage() {
        TutorAgentPlanVO confirmed = service.confirm(USER, COURSE, SESSION, RUN, 2);
        jdbc.update("""
                INSERT INTO tutor_agent_message (run_id,sequence_no,role,content,actions_json)
                VALUES ((SELECT id FROM tutor_agent_run WHERE run_key=?),3,'ASSISTANT','普通回复',NULL)
                """, RUN);
        TutorAgentPlanVO latest = service.latest(USER, COURSE, SESSION, RUN);
        assertEquals(confirmed.confirmedAt(), latest.confirmedAt());
    }

    @Test
    void withdrawnKnowledgePointCannotRemainAnAvailableTutorPlanTarget() {
        jdbc.update("UPDATE knowledge_point SET content_review_status='REVIEW_PENDING' WHERE id=?", POINT);
        assertFalse(service.propose(USER, COURSE, SESSION).stream().anyMatch(step -> "TUTOR".equals(step.type())));
        assertFalse(service.get(USER, COURSE, SESSION, RUN, 2).available());
        assertThrows(BusinessException.class, () -> service.confirm(USER, COURSE, SESSION, RUN, 2));
        assertEquals(0, count("SELECT COUNT(*) FROM tutor_agent_plan_confirmation"));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private void cleanUp() {
        if (jdbc == null) return;
        jdbc.update("DELETE FROM tutor_agent_plan_confirmation WHERE message_id IN (SELECT id FROM tutor_agent_message WHERE run_id IN (SELECT id FROM tutor_agent_run WHERE run_key=?))", RUN);
        jdbc.update("DELETE FROM tutor_agent_message WHERE run_id IN (SELECT id FROM tutor_agent_run WHERE run_key=?)", RUN);
        jdbc.update("DELETE FROM tutor_agent_run WHERE run_key=?", RUN);
        jdbc.update("DELETE FROM tutor_session WHERE session_key=?", SESSION);
        jdbc.update("DELETE FROM tutor_content WHERE id=?", CONTENT);
        jdbc.update("DELETE FROM knowledge_point WHERE id=?", POINT);
        jdbc.update("DELETE FROM user_course WHERE user_id IN (?,?)", USER, OTHER_USER);
        jdbc.update("DELETE FROM course WHERE id=?", COURSE);
        jdbc.update("DELETE FROM user WHERE id IN (?,?)", USER, OTHER_USER);
    }
}
