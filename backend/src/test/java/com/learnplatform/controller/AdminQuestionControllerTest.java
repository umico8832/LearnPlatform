package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionCorrectionReportVO;
import com.learnplatform.dto.QuestionDuplicateGroupVO;
import com.learnplatform.dto.QuestionReviewSuggestionVO;
import com.learnplatform.dto.QuestionVersionVO;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.MarkdownQuestionParser;
import com.learnplatform.service.QuestionImportExportService;
import com.learnplatform.service.QuestionReviewSuggestionService;
import com.learnplatform.service.QuestionService;
import com.learnplatform.service.QuestionSourceService;
import com.learnplatform.service.QuestionVersionService;
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

    @Mock
    private MarkdownQuestionParser markdownQuestionParser;

    @Mock
    private QuestionSourceService questionSourceService;

    @Mock
    private QuestionReviewSuggestionService questionReviewSuggestionService;

    @Mock
    private com.learnplatform.service.QuestionCorrectionReportService correctionReportService;

    @Mock
    private QuestionVersionService questionVersionService;

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
        when(questionService.getQuestionPage(eq(1), eq(10), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null)))
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
        when(questionService.getQuestionPage(eq(1), eq(10), eq("变量"), eq("SINGLE_CHOICE"), eq(1L), eq(2), eq(1), eq(null)))
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
        Long adminId = 1L;
        QuestionVO vo = buildQuestionVO(1L, "更新后题目");
        when(questionService.updateQuestion(eq(1L), any(QuestionCreateRequest.class), eq(adminId))).thenReturn(vo);

        mockMvc.perform(put("/api/admin/questions/{id}", 1L)
                        .with(mockAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"更新后题目\",\"questionType\":\"SINGLE_CHOICE\",\"courseId\":1,\"difficulty\":2,\"score\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value("更新后题目"));

        verify(questionService).updateQuestion(eq(1L), any(QuestionCreateRequest.class), eq(adminId));
    }

    @Test
    void deleteQuestion_success() throws Exception {
        Long adminId = 1L;
        doNothing().when(questionService).deleteQuestion(eq(1L), eq(adminId));

        mockMvc.perform(delete("/api/admin/questions/{id}", 1L)
                        .with(mockAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(questionService).deleteQuestion(eq(1L), eq(adminId));
    }

    @Test
    void deleteQuestion_notFound_throwsException() throws Exception {
        Long adminId = 1L;
        doThrow(new BusinessException("题目不存在")).when(questionService).deleteQuestion(eq(999L), eq(adminId));

        mockMvc.perform(delete("/api/admin/questions/{id}", 999L)
                        .with(mockAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("题目不存在"));
    }

    @Test
    void getQuestionVersions_success() throws Exception {
        QuestionVersionVO version = new QuestionVersionVO();
        version.setQuestionId(1L);
        version.setVersionNo(2);
        version.setChangeType("UPDATE");
        version.setChangeSummary("更新题目内容");
        when(questionVersionService.getQuestionVersions(eq(1L))).thenReturn(List.of(version));

        mockMvc.perform(get("/api/admin/questions/{id}/versions", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].versionNo").value(2))
                .andExpect(jsonPath("$.data[0].changeType").value("UPDATE"));
    }

    @Test
    void getReviewSuggestion_success() throws Exception {
        Long adminId = 1L;
        QuestionReviewSuggestionVO suggestion = new QuestionReviewSuggestionVO();
        suggestion.setRecommendation("APPROVE");
        suggestion.setConfidenceScore(90);
        suggestion.setSummary("题目可继续使用");
        when(questionReviewSuggestionService.generateSuggestion(eq(1L), eq(adminId))).thenReturn(suggestion);

        mockMvc.perform(get("/api/admin/questions/{id}/review-suggestion", 1L)
                        .with(mockAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recommendation").value("APPROVE"))
                .andExpect(jsonPath("$.data.confidenceScore").value(90));
    }

    @Test
    void findDuplicateQuestions_success() throws Exception {
        QuestionDuplicateGroupVO group = new QuestionDuplicateGroupVO();
        group.setMatchType("EXACT");
        group.setSimilarityScore(100);
        group.setRepresentativeContent("Java 中 == 和 equals 有什么区别？");
        group.setQuestions(List.of(
                buildQuestionVO(1L, "Java 中 == 和 equals 有什么区别？"),
                buildQuestionVO(2L, "Java中==和equals有什么区别")));
        when(questionService.findDuplicateGroups(eq(1L), eq("SHORT_ANSWER"), eq(92), eq(10)))
                .thenReturn(List.of(group));

        mockMvc.perform(get("/api/admin/questions/duplicates")
                        .param("courseId", "1")
                        .param("questionType", "SHORT_ANSWER")
                        .param("minSimilarity", "92")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].matchType").value("EXACT"))
                .andExpect(jsonPath("$.data[0].questions[1].id").value(2));
    }

    @Test
    void listCorrectionReports_success() throws Exception {
        Page<QuestionCorrectionReportVO> page = new Page<>(1, 10);
        QuestionCorrectionReportVO report = new QuestionCorrectionReportVO();
        report.setId(5L);
        report.setQuestionId(1L);
        report.setReportType("ANSWER");
        report.setStatus("OPEN");
        page.setRecords(List.of(report));
        page.setTotal(1);
        when(correctionReportService.getAdminReports(eq(1), eq(10), eq("OPEN"), eq(1L))).thenReturn(page);

        mockMvc.perform(get("/api/admin/questions/correction-reports")
                        .param("status", "OPEN")
                        .param("questionId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].reportType").value("ANSWER"));
    }

    @Test
    void processCorrectionReport_success() throws Exception {
        Long adminId = 1L;
        QuestionCorrectionReportVO report = new QuestionCorrectionReportVO();
        report.setId(5L);
        report.setStatus("RESOLVED");
        report.setHandlerId(adminId);
        report.setHandlerComment("已修正解析");
        when(correctionReportService.processReport(eq(5L), any(), eq(adminId))).thenReturn(report);

        mockMvc.perform(post("/api/admin/questions/correction-reports/{reportId}/process", 5L)
                        .with(mockAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"handlerComment\":\"已修正解析\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.handlerComment").value("已修正解析"));
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
