package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.time.BusinessTime;
import com.learnplatform.security.CustomUserDetails;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 个人课程分页必须在数据库筛选并一次批量装配当页学习概况。 */
@Tag("integration")
@SpringBootTest(properties = "logging.level.com.learnplatform.service.CourseLibraryPageIntegrationTest=INFO")
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@Transactional
class CourseLibraryPageIntegrationTest extends IntegrationTestBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(CourseLibraryPageIntegrationTest.class);
    private static final long USER = 971001L;
    private static final long OTHER = 971002L;
    private static final long STARTED = 971010L;
    private static final long NOT_STARTED = 971011L;
    private static final long DUE = 971012L;
    private static final long OTHER_COURSE = 971013L;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired SqlSessionTemplate sessions;
    @Autowired ObjectMapper json;

    @BeforeEach
    void setUp() {
        db.update("INSERT INTO user(id,username,password,role,status,deleted) VALUES "
                + "(?,'course-page-user','test','USER',1,0),(?,'course-page-other','test','USER',1,0)", USER, OTHER);
        course(STARTED, "Java !%_ \\ '课程", "并发与转义关键词", 1);
        course(NOT_STARTED, "算法入门", "从基础开始", 2);
        course(DUE, "数据库复习", "索引和事务", 3);
        course(OTHER_COURSE, "他人私有课程", "不得泄漏", 4);
        db.update("INSERT INTO user_course(user_id,course_id,create_time) VALUES "
                + "(?,?, '2026-01-01 10:00:00'),(?,?, '2026-01-02 10:00:00'),"
                + "(?,?, '2026-01-03 10:00:00'),(?,?, '2026-01-04 10:00:00')",
                USER, STARTED, USER, NOT_STARTED, USER, DUE, OTHER, OTHER_COURSE);
        db.update("""
                INSERT INTO course_learning_event(user_id,course_id,event_type,event_source,subject_type,subject_id,
                    source_record_id,idempotency_key,occurred_time)
                VALUES (?,?,'QUESTION_VIEWED','BROWSER','QUESTION',? ,?,'course-page-started', '2026-02-01 12:00:00')
                """, USER, STARTED, STARTED, STARTED);
        long questionId = DUE + 100;
        db.update("INSERT INTO question(id,content,question_type,course_id,status,visibility,deleted) "
                + "VALUES (?,'到期复习题','SINGLE_CHOICE',?,1,'PUBLIC',0)", questionId, DUE);
        db.update("INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order,deleted) "
                + "VALUES (?,'正确项','A',1,1,0)", questionId);
        db.update("INSERT INTO question_review_schedule(user_id,question_id,next_review_date,deleted) VALUES (?,?,?,0)",
                USER, questionId, BusinessTime.today().minusDays(1));
        db.update("INSERT INTO course_learning_event(user_id,course_id,event_type,event_source,subject_type,"
                        + "subject_id,source_record_id,idempotency_key,occurred_time) VALUES "
                        + "(?,?,'QUESTION_VIEWED','BROWSER','QUESTION',?,?,'course-page-due','2026-02-02 12:00:00')",
                USER, DUE, questionId, questionId);
        hiddenPracticeFactsDoNotStartCourse();
    }

    @Test
    void mostRecentLearningCanBeReadWithoutFetchingEveryOverview() throws Exception {
        db.update("UPDATE course_learning_event SET occurred_time='2026-02-03 12:00:00' "
                + "WHERE user_id=? AND course_id=?", USER, STARTED);
        mvc.perform(request().param("pageSize", "1"))
                .andExpect(jsonPath("$.data.records[0].courseId").value(DUE));
        mvc.perform(request().param("learningStatus", "STARTED").param("sortBy", "LAST_LEARNING_TIME")
                        .param("pageSize", "1"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].courseId").value(STARTED));
        mvc.perform(request().param("sortBy", "UNKNOWN")).andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void catalogUsesLiteralNameSearchAndKeepsEnabledAndDeletedBoundaries() throws Exception {
        var auth = authentication(actor(USER));
        mvc.perform(get("/api/courses").param("keyword", "  !%_ \\ '  ").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(STARTED));
        mvc.perform(get("/api/courses").param("keyword", "从基础开始").with(auth))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/courses").param("keyword", "x".repeat(100)).with(auth))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.total").value(0));
        for (String[] invalid : new String[][]{{"pageNum", "0"}, {"pageSize", "0"},
                {"keyword", "x".repeat(101)}}) {
            mvc.perform(get("/api/courses").param(invalid[0], invalid[1]).with(auth))
                    .andExpect(jsonPath("$.code").value(1001));
        }
        db.update("UPDATE course SET status=0 WHERE id=?", STARTED);
        db.update("UPDATE course SET deleted=1 WHERE id=?", DUE);
        sessions.clearCache();
        mvc.perform(get("/api/courses").param("keyword", "!%_ \\ '").with(auth))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/courses").param("keyword", "数据库复习").with(auth))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void pagesAndCombinesCourseKeywordAndLearningStatusBeforeCounting() throws Exception {
        mvc.perform(request().param("keyword", "  课程  ").param("learningStatus", "STARTED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].courseId").value(STARTED))
                .andExpect(jsonPath("$.data.records[0].overview.lastLearningTime").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].overview.answeredCount").value(0))
                .andExpect(jsonPath("$.data.records[0].overview.recommendedTargets").isArray())
                .andExpect(jsonPath("$.data.records[0].overview.tutorProgress").isArray())
                .andExpect(jsonPath("$.data.records[0].overview.latestStageAssessment").doesNotExist());

        mvc.perform(request().param("learningStatus", "NOT_STARTED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].courseId").value(NOT_STARTED))
                .andExpect(jsonPath("$.data.records[0].overview.lastLearningTime").doesNotExist());
        mvc.perform(request().param("learningStatus", "DUE_REVIEW"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].courseId").value(DUE))
                .andExpect(jsonPath("$.data.records[0].overview.dueReviewCount").value(1));
    }

    @Test
    void keepsUserIsolationHandlesLiteralKeywordAndReturnsTrueFilteredTotals() throws Exception {
        mvc.perform(request().param("keyword", "!%_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].courseId").value(STARTED));
        mvc.perform(request().param("keyword", "\\"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].courseId").value(STARTED));
        mvc.perform(request().param("keyword", "'课程"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].courseId").value(STARTED));
        mvc.perform(request().param("courseId", Long.toString(OTHER_COURSE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
        mvc.perform(request().param("pageNum", "2").param("pageSize", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(1));
        mvc.perform(request().param("pageNum", "3").param("pageSize", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @Test
    void rejectsInvalidFiltersAndDoesNotMakePerCourseQueriesAsPageGrows() throws Exception {
        mvc.perform(request().param("learningStatus", "UNKNOWN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1001));
        mvc.perform(request().param("pageNum", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1001));
        mvc.perform(request().param("keyword", "x".repeat(101)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1001));

        for (int index = 0; index < 17; index++) {
            db.update("INSERT INTO course(name,status,deleted) VALUES (?,1,0)", "分页扩展课程" + index);
            long id = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            db.update("INSERT INTO user_course(user_id,course_id,create_time) VALUES (?,?,'2026-01-01 10:00:00')",
                    USER, id);
        }
        sessions.clearCache();
        long before = statementCount();
        mvc.perform(request().param("pageSize", "1")).andExpect(status().isOk());
        long one = statementCount() - before;
        sessions.clearCache();
        before = statementCount();
        mvc.perform(request().param("pageSize", "500")).andExpect(status().isOk());
        long all = statementCount() - before;
        LOGGER.info("个人课程分页 SQL 计数：1门={}，20门={}", one, all);
        assertEquals(one, all, "query count grew with page size: " + one + " -> " + all);
    }

    @Test
    void pageOverviewMatchesTheExistingSingleCourseOverviewWithoutDroppingNestedFacts() throws Exception {
        long point = DUE + 200;
        long content = DUE + 300;
        long assessment = DUE + 400;
        db.update("INSERT INTO knowledge_point(id,name,course_id,parent_id,sort_order,deleted,content_review_status) "
                + "VALUES (?,'事务教学',?,0,1,0,'REVIEWED')", point, DUE);
        db.update("INSERT INTO tutor_content(id,knowledge_point_id,content_key,content_version,"
                        + "review_status,title,lesson_json,check_json) "
                + "VALUES (?,?,?,1,'REVIEWED','事务讲解','{}','{}')", content, point, "course-page-tutor-" + DUE);
        db.update("INSERT INTO tutor_session(id,session_key,user_id,course_id,knowledge_point_id,"
                        + "tutor_content_id,create_time) "
                + "VALUES (?,UUID(),?,?,?,?, '2026-02-02 10:00:00')", content + 1, USER, DUE, point, content);
        db.update("INSERT INTO course_stage_assessment(id,user_id,course_id,status,selection_strategy,"
                        + "question_count,correct_count,active_session_key,complete_time) "
                        + "VALUES (?,? ,?,'COMPLETED','COURSE_SEQUENCE_FALLBACK',1,1,NULL,"
                        + "'2026-02-03 10:00:00')",
                assessment, USER, DUE);
        db.update("INSERT INTO course_stage_assessment_question(id,assessment_id,question_id,sort_order,"
                        + "question_type,content_snapshot,options_snapshot,correct_answer_snapshot,score,"
                        + "user_answer,is_correct,answered_time) "
                        + "VALUES (?,?,?,1,'SINGLE_CHOICE','快照题','[]','A',1,'A',1,"
                        + "'2026-02-03 10:00:00')",
                assessment + 1, assessment, DUE + 100);
        JsonNode paged = json.readTree(mvc.perform(request().param("courseId", Long.toString(DUE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode single = json.readTree(mvc.perform(get("/api/my-courses/{courseId}/overview", DUE)
                .with(authentication(actor(USER)))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode fromPage = paged.path("data").path("records").get(0).path("overview");
        JsonNode fromSingle = single.path("data");
        assertEquals(fromSingle.path("answeredCount"), fromPage.path("answeredCount"));
        assertEquals(fromSingle.path("correctCount"), fromPage.path("correctCount"));
        assertEquals(fromSingle.path("dueReviewCount"), fromPage.path("dueReviewCount"));
        assertEquals(fromSingle.path("unresolvedWrongCount"), fromPage.path("unresolvedWrongCount"));
        assertEquals(fromSingle.path("lastLearningTime"), fromPage.path("lastLearningTime"));
        assertEquals(fromSingle.path("recommendedTargets"), fromPage.path("recommendedTargets"));
        assertEquals(fromSingle.path("tutorProgress"), fromPage.path("tutorProgress"));
        assertEquals(fromSingle.path("latestStageAssessment"), fromPage.path("latestStageAssessment"));
        assertTrue(fromPage.path("tutorProgress").isArray());
        assertEquals("事务讲解", fromPage.path("tutorProgress").get(0).path("title").asText());
        assertEquals(assessment, fromPage.path("latestStageAssessment").path("id").asLong());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
        return get("/api/my-courses/page").with(authentication(actor(USER)));
    }

    private UsernamePasswordAuthenticationToken actor(long userId) {
        return new UsernamePasswordAuthenticationToken(new CustomUserDetails(userId, "course-page", "USER"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private void course(long id, String name, String description, int sortOrder) {
        db.update("INSERT INTO course(id,name,description,sort_order,status,deleted) VALUES (?,?,?,?,1,0)",
                id, name, description, sortOrder);
    }

    private void hiddenPracticeFactsDoNotStartCourse() {
        long deletedQuestion = NOT_STARTED + 10000;
        long privateQuestion = NOT_STARTED + 20000;
        db.update("INSERT INTO question(id,content,question_type,course_id,status,visibility,deleted) "
                + "VALUES (?,'已删除历史题','SINGLE_CHOICE',?,1,'PUBLIC',1)", deletedQuestion, NOT_STARTED);
        db.update("INSERT INTO question(id,content,question_type,course_id,status,visibility,owner_user_id,deleted) "
                        + "VALUES (?,'他人私有历史题','SINGLE_CHOICE',?,1,'PRIVATE',?,0)",
                privateQuestion, NOT_STARTED, OTHER);
        db.update("INSERT INTO practice_record(user_id,question_id,user_answer,is_correct,create_time) "
                        + "VALUES (?,?, 'A',1,'2026-02-02 10:00:00'),(?,?, 'A',1,'2026-02-03 10:00:00')",
                USER, deletedQuestion, USER, privateQuestion);
    }

    private long statementCount() {
        return db.queryForObject("SHOW SESSION STATUS LIKE 'Questions'", (row, index) -> row.getLong(2));
    }
}
