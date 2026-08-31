package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.LearningReportVO;
import com.learnplatform.dto.StatisticsVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.LearningReportService;
import com.learnplatform.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * StatisticsController MockMvc 集成测试
 */
@ExtendWith(MockitoExtension.class)
class StatisticsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StatisticsService statisticsService;

    @Mock
    private LearningReportService learningReportService;

    @InjectMocks
    private StatisticsController statisticsController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(statisticsController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
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

    // ======================== 学习概览 ========================

    @Test
    void getOverview_returnsStatistics() throws Exception {
        StatisticsVO vo = new StatisticsVO();
        vo.setTotalPractice(200);
        vo.setCorrectRate(0.82);
        vo.setTodayPractice(15);
        vo.setStreakDays(7);

        when(statisticsService.getUserStatistics(eq(1L))).thenReturn(vo);

        mockMvc.perform(get("/api/statistics/overview").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalPractice").value(200))
                .andExpect(jsonPath("$.data.correctRate").value(0.82))
                .andExpect(jsonPath("$.data.todayPractice").value(15))
                .andExpect(jsonPath("$.data.streakDays").value(7));
    }

    // ======================== 每日趋势 ========================

    @Test
    void getDailyTrend_returnsList() throws Exception {
        when(statisticsService.getDailyTrend(eq(1L)))
                .thenReturn(List.of(
                        Map.of("date", "2026-06-08", "count", 10),
                        Map.of("date", "2026-06-09", "count", 20)
                ));

        mockMvc.perform(get("/api/statistics/daily-trend").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].date").value("2026-06-08"));
    }

    // ======================== 课程统计 ========================

    @Test
    void getCourseStats_returnsList() throws Exception {
        when(statisticsService.getCourseStats(eq(1L)))
                .thenReturn(List.of(
                        Map.of("courseName", "Java 基础", "correctRate", 0.85),
                        Map.of("courseName", "数据结构", "correctRate", 0.72)
                ));

        mockMvc.perform(get("/api/statistics/course-stats").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].courseName").value("Java 基础"));
    }

    // ======================== 学习报告 ========================

    @Test
    void getLearningReport_returnsReport() throws Exception {
        LearningReportVO report = new LearningReportVO();
        report.setMonthTotalPractice(150);
        report.setMonthCorrectRate(0.8);
        report.setLearningEffectScore(76.5);
        report.setLearningEffectLevel("IMPROVING");

        when(learningReportService.getLearningReport(eq(1L))).thenReturn(report);

        mockMvc.perform(get("/api/statistics/learning-report").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.monthTotalPractice").value(150))
                .andExpect(jsonPath("$.data.monthCorrectRate").value(0.8))
                .andExpect(jsonPath("$.data.learningEffectScore").value(76.5))
                .andExpect(jsonPath("$.data.learningEffectLevel").value("IMPROVING"));
    }
}
