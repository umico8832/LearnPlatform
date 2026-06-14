package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.ExamPaperCreateRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamPaperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminExamController MockMvc 集成测试
 */
@ExtendWith(MockitoExtension.class)
class AdminExamControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ExamPaperService examPaperService;

    @InjectMocks
    private AdminExamController adminExamController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminExamController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    private RequestPostProcessor mockAdmin(Long userId) {
        return request -> {
            CustomUserDetails details = new CustomUserDetails(userId, "admin", "ADMIN");
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    details, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            return request;
        };
    }

    private ExamPaperVO buildExamPaperVO(Long id, String title) {
        ExamPaperVO vo = new ExamPaperVO();
        vo.setId(id);
        vo.setTitle(title);
        vo.setCourseId(1L);
        vo.setCourseName("测试课程");
        vo.setTotalScore(100);
        vo.setDuration(60);
        vo.setQuestionCount(10);
        vo.setStatus(0);
        vo.setCreateBy(1L);
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }

    @Test
    void list_success() throws Exception {
        Page<ExamPaperVO> page = new Page<>(1, 10);
        page.setRecords(List.of(buildExamPaperVO(1L, "期末考试")));
        page.setTotal(1);
        when(examPaperService.getExamPaperPage(eq(1), eq(10), eq(null), eq(null))).thenReturn(page);

        mockMvc.perform(get("/api/admin/exam-papers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].title").value("期末考试"));
    }

    @Test
    void list_withFilters() throws Exception {
        Page<ExamPaperVO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(examPaperService.getExamPaperPage(eq(1), eq(10), eq(1L), eq(1))).thenReturn(page);

        mockMvc.perform(get("/api/admin/exam-papers")
                        .param("courseId", "1")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void detail_success() throws Exception {
        ExamPaperVO vo = buildExamPaperVO(1L, "期中考试");
        when(examPaperService.getExamPaperById(eq(1L))).thenReturn(vo);

        mockMvc.perform(get("/api/admin/exam-papers/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("期中考试"));

        verify(examPaperService).getExamPaperById(eq(1L));
    }

    @Test
    void create_success() throws Exception {
        Long adminId = 1L;
        ExamPaperVO vo = buildExamPaperVO(1L, "新建试卷");
        when(examPaperService.createExamPaper(any(ExamPaperCreateRequest.class), eq(adminId))).thenReturn(vo);

        mockMvc.perform(post("/api/admin/exam-papers")
                        .with(mockAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新建试卷\",\"courseId\":1,\"duration\":60,\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("新建试卷"));
    }

    @Test
    void update_success() throws Exception {
        ExamPaperVO vo = buildExamPaperVO(1L, "更新试卷");
        when(examPaperService.updateExamPaper(eq(1L), any(ExamPaperCreateRequest.class))).thenReturn(vo);

        mockMvc.perform(put("/api/admin/exam-papers/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"更新试卷\",\"courseId\":1,\"duration\":60,\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("更新试卷"));

        verify(examPaperService).updateExamPaper(eq(1L), any(ExamPaperCreateRequest.class));
    }

    @Test
    void delete_success() throws Exception {
        doNothing().when(examPaperService).deleteExamPaper(eq(1L));

        mockMvc.perform(delete("/api/admin/exam-papers/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(examPaperService).deleteExamPaper(eq(1L));
    }

    @Test
    void publish_success() throws Exception {
        doNothing().when(examPaperService).publishExamPaper(eq(1L));

        mockMvc.perform(post("/api/admin/exam-papers/{id}/publish", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(examPaperService).publishExamPaper(eq(1L));
    }
}