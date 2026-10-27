package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.common.time.BusinessTime;
import com.learnplatform.controller.CustomUserDetailsArgumentResolver;
import com.learnplatform.controller.ExamController;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.exam.ExamAutomaticGradingGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@Transactional
class ExamListFiltersIntegrationTest extends IntegrationTestBase {
    private static final long USER = 978001;
    private static final long OTHER = 978002;
    private static final long COURSE = 978010;
    private static final long OTHER_COURSE = 978011;
    private static final long PAPER = 978020;
    private static final long OTHER_PAPER = 978021;

    @Autowired private JdbcTemplate db;
    @Autowired private MockMvc mvc;
    @Autowired private ExamRecordMapper records;
    @Autowired private ExamPaperMapper papers;
    @Autowired private ExamRecordViewService views;

    @BeforeEach
    void prepare() {
        db.update("INSERT INTO user(id,username,password,role,status,deleted) VALUES "
                + "(?,'exam-list-owner','test','USER',1,0),(?,'exam-list-other','test','USER',1,0)", USER, OTHER);
        db.update("INSERT INTO course(id,name,status,deleted) VALUES (?, '考试筛选课程',1,0),"
                + "(?,'其他考试课程',1,0)", COURSE, OTHER_COURSE);
        db.update("INSERT INTO exam_paper(id,title,course_id,total_score,duration,status,create_by,visibility,deleted) "
                + "VALUES (?,?,?,100,60,1,?,'PUBLIC',0),(?,?,?,100,60,1,?,'PUBLIC',0)",
                PAPER, "Exam 50%_!\\'题", COURSE, USER,
                OTHER_PAPER, "Exam 50%_!\\'题", OTHER_COURSE, USER);
    }

    @Test
    void combinesFiltersAndMatchesLiteralWildcardsWithoutReadingAnotherUsersRecords() throws Exception {
        long expected = record(USER, PAPER, 1, Instant.now());
        record(USER, OTHER_PAPER, 1, Instant.now());
        record(OTHER, PAPER, 1, Instant.now());
        mvc.perform(request().param("courseId", String.valueOf(COURSE)).param("status", "1")
                        .param("keyword", "  50%_!\\'  ").param("userId", String.valueOf(OTHER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(expected));
        mvc.perform(request().param("keyword", "%' OR 1=1 --"))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(request().param("courseId", "-987654"))
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @Test
    void keepsDefaultRecordsAndAccurateTotalsOnEmptyAndOverflowPages() throws Exception {
        record(USER, PAPER, 1, Instant.now());
        record(USER, OTHER_PAPER, 3, Instant.now());
        record(OTHER, PAPER, 1, Instant.now());
        mvc.perform(request()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.current").value(1)).andExpect(jsonPath("$.data.size").value(10));
        mvc.perform(request().param("keyword", " \t ").param("pageNum", "3").param("pageSize", "1"))
                .andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.records").isEmpty());
        mvc.perform(request().param("pageSize", "999")).andExpect(jsonPath("$.data.size").value(500));
        mvc.perform(request().param("status", "3")).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value(3));
    }

    @Test
    void validatesPageStatusAndKeywordLength() throws Exception {
        for (String[] pair : new String[][]{{"pageNum", "0"}, {"pageSize", "0"}, {"status", "4"},
                {"status", "-1"}, {"keyword", "字".repeat(101)}}) {
            mvc.perform(request().param(pair[0], pair[1])).andExpect(jsonPath("$.code").value(1001));
        }
        mvc.perform(request().param("keyword", "字".repeat(100))).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void filtersTheSameEffectiveTimeoutStateThatItReturnsAcrossTheRepeatedDstHour() throws Exception {
        Instant start = Instant.parse("2026-11-01T05:30:00Z");
        long expired = record(USER, PAPER, 0, start);
        long active = record(USER, PAPER, 0, start.plusSeconds(1800));
        long persisted = record(USER, PAPER, 2, start.minusSeconds(7200));
        MockMvc fixed = fixedMvc(Instant.parse("2026-11-01T06:31:00Z"));
        try {
            fixed.perform(fixedRequest().param("status", "0")).andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value(active));
            fixed.perform(fixedRequest().param("status", "2").param("pageSize", "1"))
                    .andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.records.length()").value(1))
                    .andExpect(jsonPath("$.data.records[0].status").value(2));
            assertEquals(0, db.queryForObject("SELECT status FROM exam_record WHERE id=?", Integer.class, expired));
            assertEquals(2, db.queryForObject("SELECT status FROM exam_record WHERE id=?", Integer.class, persisted));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void historicalLocalStartsUseTheExistingNewYorkProjectionAndDeletedPapersStayHistorical() throws Exception {
        long id = record(USER, PAPER, 0, Instant.parse("2026-11-01T05:30:00Z"));
        db.update("UPDATE exam_record SET start_instant_utc=NULL WHERE id=?", id);
        MockMvc fixed = fixedMvc(Instant.parse("2026-11-01T06:31:00Z"));
        try {
            fixed.perform(fixedRequest().param("status", "2")).andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].status").value(2));
            db.update("UPDATE exam_paper SET deleted=1 WHERE id=?", PAPER);
            fixed.perform(fixedRequest()).andExpect(jsonPath("$.data.total").value(1));
            fixed.perform(fixedRequest().param("courseId", String.valueOf(COURSE)))
                    .andExpect(jsonPath("$.data.total").value(0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private MockHttpServletRequestBuilder request() {
        return get("/api/exam/records").with(authentication(auth()));
    }

    private MockHttpServletRequestBuilder fixedRequest() {
        SecurityContextHolder.getContext().setAuthentication(auth());
        return get("/api/exam/records");
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(new CustomUserDetails(USER, "exam-list-owner", "USER"),
                null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private MockMvc fixedMvc(Instant now) {
        ExamSessionService sessions = new ExamSessionService(records, papers, views,
                Clock.fixed(now, BusinessTime.ZONE), mock(ExamAutomaticGradingGuard.class));
        ExamService service = new ExamService(sessions, mock(ExamSubmissionService.class), views,
                mock(ExamSubmissionRewardService.class));
        return MockMvcBuilders.standaloneSetup(new ExamController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver()).build();
    }

    private long record(long owner, long paper, int state, Instant start) {
        db.update("INSERT INTO exam_record(user_id,exam_paper_id,start_time,start_instant_utc,status,total_score,"
                        + "create_time) VALUES (?,?,?,?,?,100,?)", owner, paper,
                LocalDateTime.ofInstant(start, BusinessTime.ZONE), LocalDateTime.ofInstant(start, ZoneOffset.UTC),
                state, LocalDateTime.ofInstant(start, BusinessTime.ZONE));
        return db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
