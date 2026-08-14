package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.UserCourseVO;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.dto.CourseStageAssessmentCreateRequest;
import com.learnplatform.dto.CourseStageAssessmentSubmitRequest;
import com.learnplatform.dto.CourseStageAssessmentVO;
import com.learnplatform.dto.CourseStageAssessmentSummaryVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.CourseLibraryService;
import com.learnplatform.service.CourseOverviewService;
import com.learnplatform.service.CourseStageAssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourseLibraryControllerTest {

    private MockMvc mockMvc;

    @Mock private CourseLibraryService courseLibraryService;
    @Mock private CourseOverviewService courseOverviewService;
    @Mock private CourseStageAssessmentService stageAssessmentService;

    @BeforeEach
    void setUp() {
        CourseLibraryController controller = new CourseLibraryController(
                courseLibraryService, courseOverviewService, stageAssessmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    @Test
    void addCourseUsesAuthenticatedUser() throws Exception {
        UserCourseVO vo = new UserCourseVO();
        vo.setCourseId(10L);
        vo.setCourseName("408 数据结构");
        when(courseLibraryService.addCourse(7L, 10L)).thenReturn(vo);

        mockMvc.perform(post("/api/my-courses/10").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.courseId").value(10))
                .andExpect(jsonPath("$.data.courseName").value("408 数据结构"));

        verify(courseLibraryService).addCourse(7L, 10L);
    }

    @Test
    void getMyCoursesUsesAuthenticatedUser() throws Exception {
        when(courseLibraryService.getMyCourses(7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/my-courses").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(courseLibraryService).getMyCourses(7L);
    }

    @Test
    void getCourseOverviewUsesAuthenticatedUser() throws Exception {
        CourseOverviewVO overview = new CourseOverviewVO();
        overview.setCourseId(10L);
        overview.setAnsweredCount(3);
        when(courseOverviewService.getOverview(7L, 10L)).thenReturn(overview);

        mockMvc.perform(get("/api/my-courses/10/overview").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value(10))
                .andExpect(jsonPath("$.data.answeredCount").value(3));

        verify(courseOverviewService).getOverview(7L, 10L);
    }

    @Test
    void startLearningAcceptsNoClientSelectedKnowledgePoint() throws Exception {
        CourseOverviewVO.LearningTargetVO target = new CourseOverviewVO.LearningTargetVO();
        target.setType("TUTOR");
        target.setKnowledgePointId(41L);
        target.setTitle("继续 AI 教学");
        when(courseOverviewService.selectStartTarget(7L, 10L)).thenReturn(target);

        mockMvc.perform(post("/api/my-courses/10/start-learning").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("TUTOR"))
                .andExpect(jsonPath("$.data.knowledgePointId").value(41));

        verify(courseOverviewService).selectStartTarget(7L, 10L);
    }

    @Test
    void startsAndSubmitsStageAssessmentAsAuthenticatedUser() throws Exception {
        CourseStageAssessmentVO started = new CourseStageAssessmentVO();
        started.setId(51L);
        started.setCourseId(10L);
        started.setStatus("IN_PROGRESS");
        when(stageAssessmentService.start(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(CourseStageAssessmentCreateRequest.class))).thenReturn(started);
        CourseStageAssessmentVO completed = new CourseStageAssessmentVO();
        completed.setId(51L);
        completed.setStatus("COMPLETED");
        completed.setCorrectCount(1);
        when(stageAssessmentService.submit(
                org.mockito.ArgumentMatchers.eq(51L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(CourseStageAssessmentSubmitRequest.class))).thenReturn(completed);

        mockMvc.perform(post("/api/my-courses/10/stage-assessments").with(mockUser(7L))
                        .contentType("application/json").content("{\"questionCount\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        mockMvc.perform(post("/api/my-courses/stage-assessments/51/submit").with(mockUser(7L))
                        .contentType("application/json")
                        .content("{\"answers\":[{\"assessmentQuestionId\":61,\"userAnswer\":\"A\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.correctCount").value(1));
    }

    @Test
    void listsAndReadsOwnedCompletedStageAssessments() throws Exception {
        CourseStageAssessmentSummaryVO summary = new CourseStageAssessmentSummaryVO();
        summary.setId(51L);
        summary.setQuestionCount(5);
        summary.setCorrectCount(3);
        Page<CourseStageAssessmentSummaryVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(summary));
        when(stageAssessmentService.listCompleted(7L, 10L, 1, 10)).thenReturn(page);
        CourseStageAssessmentVO detail = new CourseStageAssessmentVO();
        detail.setId(51L);
        detail.setStatus("COMPLETED");
        when(stageAssessmentService.getCompleted(51L, 7L)).thenReturn(detail);

        mockMvc.perform(get("/api/my-courses/10/stage-assessments").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(51))
                .andExpect(jsonPath("$.data.records[0].correctCount").value(3));
        mockMvc.perform(get("/api/my-courses/stage-assessments/51").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    private RequestPostProcessor mockUser(Long userId) {
        return request -> {
            CustomUserDetails details = new CustomUserDetails(userId, "testuser", "USER");
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    details, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            return request;
        };
    }
}
