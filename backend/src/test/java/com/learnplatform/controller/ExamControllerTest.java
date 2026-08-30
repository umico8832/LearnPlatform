package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamPaperService;
import com.learnplatform.service.ExamService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExamControllerTest {

    @Mock
    private ExamService examService;

    @Mock
    private ExamPaperService examPaperService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ExamController(examService),
                        new ExamPaperController(examPaperService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    @Test
    void listsAccessiblePapersAndReadsDetail() throws Exception {
        ExamPaperVO paper = new ExamPaperVO();
        paper.setId(2L);
        paper.setTitle("测试试卷");
        Page<ExamPaperVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(paper));
        when(examPaperService.getAccessiblePublishedExamPaperPage(7L, 1, 10, 3L))
                .thenReturn(page);
        when(examPaperService.getAccessiblePublishedExamPaperById(2L, 7L)).thenReturn(paper);

        mockMvc.perform(get("/api/exam/papers")
                        .param("courseId", "3")
                        .with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(2));
        mockMvc.perform(get("/api/exam/papers/2").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("测试试卷"));
    }

    @Test
    void startsAndReadsAuthenticatedTimedExamSession() throws Exception {
        ExamRecordVO session = examRecord(40L);
        when(examService.startExam(2L, 7L)).thenReturn(session);
        when(examService.getExamSession(40L, 7L)).thenReturn(session);

        mockMvc.perform(post("/api/exam/start/2").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(40));
        mockMvc.perform(get("/api/exam/records/40/session").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examPaperId").value(2))
                .andExpect(jsonPath("$.data.answers").doesNotExist());

        verify(examService).startExam(2L, 7L);
        verify(examService).getExamSession(40L, 7L);
    }

    @Test
    void submitsReadsResultAndListsAuthenticatedRecords() throws Exception {
        ExamRecordVO record = examRecord(40L);
        Page<ExamRecordVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(record));
        when(examService.submitExam(any(ExamSubmitRequest.class), eq(7L))).thenReturn(record);
        when(examService.getExamResult(40L, 7L)).thenReturn(record);
        when(examService.getExamList(7L, 1, 10)).thenReturn(page);

        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setExamRecordId(40L);
        ExamSubmitRequest.AnswerItem answer = new ExamSubmitRequest.AnswerItem();
        answer.setQuestionId(10L);
        answer.setUserAnswer("A");
        request.setAnswers(List.of(answer));
        mockMvc.perform(post("/api/exam/submit")
                        .with(mockUser(7L))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(40));
        mockMvc.perform(get("/api/exam/result/40").with(mockUser(7L)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/exam/records").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    private ExamRecordVO examRecord(Long id) {
        ExamRecordVO record = new ExamRecordVO();
        record.setId(id);
        record.setExamPaperId(2L);
        record.setStatus(0);
        return record;
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
