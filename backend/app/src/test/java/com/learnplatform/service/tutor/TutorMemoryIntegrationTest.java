package com.learnplatform.service.tutor;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.TutorMemoryUpdateRequest;
import com.learnplatform.dto.TutorMemoryVO;
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
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class TutorMemoryIntegrationTest extends IntegrationTestBase {
    private static final long USER = 989401;
    private static final long OTHER = 989402;
    private static final long COURSE = 989410;
    @Autowired private TutorMemoryService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach void prepare() {
        clean();
        jdbc.update("INSERT INTO course (id,name,status,deleted) VALUES (?,'记忆测试课程',1,0)", COURSE);
        jdbc.update("INSERT INTO user_course (user_id,course_id) VALUES (?,?),(?,?)", USER, COURSE, OTHER, COURSE);
    }

    @AfterEach void clean() {
        if (jdbc == null) return;
        jdbc.update("DELETE FROM tutor_course_memory WHERE course_id=?", COURSE);
        jdbc.update("DELETE FROM user_course WHERE course_id=?", COURSE);
        jdbc.update("DELETE FROM course WHERE id=?", COURSE);
    }

    @Test void savesCorrectsAndErasesExplicitMemoryWithoutWritingLearningFacts() {
        assertEquals(new TutorMemoryVO(0, null, null), service.get(USER, COURSE));
        var first = service.save(USER, COURSE, new TutorMemoryUpdateRequest(0L, "EXAMPLES", "  理解栈顶变化  "));
        assertEquals(new TutorMemoryVO(1, "EXAMPLES", "理解栈顶变化"), first);
        assertEquals(first, service.get(USER, COURSE));
        var corrected = service.save(USER, COURSE, new TutorMemoryUpdateRequest(1L, "STEP_BY_STEP", "理解队列"));
        assertEquals(2L, corrected.revision());
        assertEquals("理解队列", corrected.goal());
        assertEquals(new TutorMemoryVO(3, null, null), service.delete(USER, COURSE, 2L));
        assertNull(jdbc.queryForObject("SELECT goal FROM tutor_course_memory WHERE user_id=? AND course_id=?",
                String.class, USER, COURSE));
        assertTrue(service.promptContext(USER, COURSE).contains("\"goal\":null"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM practice_record WHERE user_id=?", Integer.class, USER));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM course_learning_event WHERE user_id=?", Integer.class, USER));
    }

    @Test void staleTabsCannotOverwriteCorrectionsOrRecreateDeletedValues() {
        service.save(USER, COURSE, new TutorMemoryUpdateRequest(0L, "CONCISE", "旧目标"));
        service.delete(USER, COURSE, 1L);
        for (long stale : List.of(0L, 1L)) {
            assertEquals(ResultCode.BUSINESS_ERROR.getCode(), assertThrows(BusinessException.class,
                    () -> service.save(USER, COURSE, new TutorMemoryUpdateRequest(stale, "EXAMPLES", "旧目标"))).getCode());
        }
        service.save(USER, COURSE, new TutorMemoryUpdateRequest(2L, null, "新目标"));
        assertThrows(BusinessException.class, () -> service.delete(USER, COURSE, 2L));
        assertEquals("新目标", service.get(USER, COURSE).goal());
    }

    @Test void isolatesOwnersAndRequiresCurrentCourseMembershipAndAvailability() {
        service.save(USER, COURSE, new TutorMemoryUpdateRequest(0L, null, "本人的目标"));
        assertEquals(new TutorMemoryVO(0, null, null), service.get(OTHER, COURSE));
        service.save(OTHER, COURSE, new TutorMemoryUpdateRequest(0L, "CONCISE", "另一位用户"));
        assertEquals("本人的目标", service.get(USER, COURSE).goal());
        assertThrows(BusinessException.class, () -> service.get(USER, COURSE + 1));
        jdbc.update("DELETE FROM user_course WHERE user_id=? AND course_id=?", USER, COURSE);
        assertEquals(ResultCode.NOT_FOUND.getCode(), assertThrows(BusinessException.class,
                () -> service.get(USER, COURSE)).getCode());
        assertThrows(BusinessException.class,
                () -> service.save(USER, COURSE, new TutorMemoryUpdateRequest(1L, null, "越权更新")));
        assertThrows(BusinessException.class, () -> service.delete(USER, COURSE, 1L));
        jdbc.update("UPDATE course SET status=0 WHERE id=?", COURSE);
        assertThrows(BusinessException.class, () -> service.get(OTHER, COURSE));
    }

    @Test void concurrentInitialSavesHaveOneWinnerAndNoSilentOverwrite() throws Exception {
        var ready = new CountDownLatch(1);
        Callable<Boolean> save = () -> {
            ready.await();
            try {
                service.save(USER, COURSE, new TutorMemoryUpdateRequest(0L, null, Thread.currentThread().getName()));
                return true;
            } catch (BusinessException exception) {
                assertEquals(ResultCode.BUSINESS_ERROR.getCode(), exception.getCode());
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(save);
            var second = executor.submit(save);
            ready.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
        assertEquals(1L, service.get(USER, COURSE).revision());
    }

    @Test void rejectsInvalidOrEmptyMemoryBeforeWriting() {
        for (var request : List.of(new TutorMemoryUpdateRequest(0L, null, "  "),
                new TutorMemoryUpdateRequest(-1L, "EXAMPLES", null),
                new TutorMemoryUpdateRequest(0L, "ADMIN", "目标"),
                new TutorMemoryUpdateRequest(0L, null, "长".repeat(501)))) {
            assertEquals(ResultCode.VALIDATION_ERROR.getCode(), assertThrows(BusinessException.class,
                    () -> service.save(USER, COURSE, request)).getCode());
        }
        assertEquals(new TutorMemoryVO(0, null, null), service.get(USER, COURSE));
        assertEquals(new TutorMemoryVO(0, null, null), service.delete(USER, COURSE, 0L));
    }
}
