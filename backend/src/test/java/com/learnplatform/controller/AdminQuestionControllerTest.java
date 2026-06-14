package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.QuestionImportExportService;
import com.learnplatform.service.QuestionService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminQuestionController MockMvc 集成测试
 */
@ExtendWith(MockitoExtension.class)
class AdminQuestionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private QuestionService questionService;

    @Mock
    private QuestionImportExportService questionImportExportService;

    @InjectMocks
    private AdminQuestionController adminQuestionController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminQuestionController)
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

    private QuestionVO buildQuestionVO(Long id, String content) {
        QuestionVO vo = new QuestionVO();
        vo.setId(id);
        vo.setContent(content);
        vo.setQuestionType("SINGLE_CHOICE");
        vo.setCourseId(1L);
        vo.setCourseName("测试课程");
        vo.setDifficulty(2);
        vo.setScore(5);
        vo.setStatus(1);
        return vo;
    }

    @Test
    void listQuestions_success() throws Exception {
        Page<QuestionVO> page = new Page<>(1, 10);
        page.setRecords(List.of(buildQuestionVO(1L, "什么是变量？")));
        page.setTotal(1);
        when(questionService.getQuestionPage(eq(1), eq(10), eq(null), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].content").value("什么是变量？"));
    }

    @Test
    void listQuestions_withFilters() throws Exception {
        Page<QuestionVO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(questionService.getQuestionPage(eq(1), eq(10), eq("变量"), eq("SINGLE_CHOICE"), eq(1L), eq(2), eq(1)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/questions")
                        .param("keyword", "变量")
                        .param("questionType", "SINGLE_CHOICE")
                        .param("courseId", "1")
                        .param("difficulty", "2")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void getQuestion_success() throws Exception {
        QuestionVO vo = buildQuestionVO(1L, "什么是变量？");
        when(questionService.getQuestionById(eq(1L))).thenReturn(vo);

        mockMvc.perform(get("/api/admin/questions/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value("什么是变量？"));

        verify(questionService).getQuestionById(eq(1L));
    }

    @Test
    void getQuestion_notFound_throwsException() throws Exception {
        when(questionService.getQuestionById(eq(999L))).thenThrow(new BusinessException("题目不存在"));

        mockMvc.perform(get("/api/admin/questions/{id}", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("题目不存在"));
    }

    @Test
    void createQuestion_success() throws Exception {
        Long adminId = 1L;
        QuestionVO vo = buildQuestionVO(1L, "新题目");
        when(questionService.createQuestion(any(QuestionCreateRequest.class), eq(adminId))).thenReturn(vo);

        mockMvc.perform(post("/api/admin/questions")
                        .with(mockAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"新题目\",\"questionType\":\"SINGLE_CHOICE\",\"courseId\":1,\"difficulty\":2,\"score\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value("新题目"));
    }

    @Test
    void updateQuestion_success() throws Exception {
        QuestionVO vo = buildQuestionVO(1L, "更新后题目");
        when(questionService.updateQuestion(eq(1L), any(QuestionCreateRequest.class))).thenReturn(vo);

        mockMvc.perform(put("/api/admin/questions/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"更新后题目\",\"questionType\":\"SINGLE_CHOICE\",\"courseId\":1,\"difficulty\":2,\"score\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value("更新后题目"));

        verify(questionService).updateQuestion(eq(1L), any(QuestionCreateRequest.class));
    }

    @Test
    void deleteQuestion_success() throws Exception {
        doNothing().when(questionService).deleteQuestion(eq(1L));

        mockMvc.perform(delete("/api/admin/questions/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(questionService).deleteQuestion(eq(1L));
    }

    @Test
    void deleteQuestion_notFound_throwsException() throws Exception {
        doThrow(new BusinessException("题目不存在")).when(questionService).deleteQuestion(eq(999L));

        mockMvc.perform(delete("/api/admin/questions/{id}", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("题目不存在"));
    }

    @Test
    void importQuestions_emptyFile_returnsError() throws Exception {
        Long adminId = 1L;

        // Create an empty mock file
        org.springframework.mock.web.MockMultipartFile emptyFile =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "test.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        new byte[0]);

        mockMvc.perform(multipart("/api/admin/questions/import")
                        .file(emptyFile)
                        .with(mockAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("上传文件不能为空"));
    }

    @Test
    void importQuestions_invalidExtension_returnsError() throws Exception {
        Long adminId = 1L;

        org.springframework.mock.web.MockMultipartFile txtFile =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "test.txt",
                        "text/plain",
                        "some content".getBytes());

        mockMvc.perform(multipart("/api/admin/questions/import")
                        .file(txtFile)
                        .with(mockAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("仅支持 .xlsx 或 .xls 文件"));
    }
}