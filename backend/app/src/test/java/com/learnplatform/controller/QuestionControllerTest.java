package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.service.QuestionCorrectionReportService;
import com.learnplatform.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QuestionControllerTest {

    private MockMvc mockMvc;

    @Mock private QuestionService questionService;
    @Mock private QuestionCorrectionReportService correctionReportService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new QuestionController(questionService, correctionReportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listQuestions_passesPreciseQuestionAndKnowledgePointFilters() throws Exception {
        Page<QuestionVO> page = new Page<>(1, 5, 0);
        page.setRecords(List.of());
        when(questionService.getEnabledQuestionPage(
                eq(1), eq(5), eq("SINGLE_CHOICE"), eq(2L), eq(3), eq(21L), eq(31L)))
                .thenReturn(page);

        mockMvc.perform(get("/api/questions")
                        .param("pageSize", "5")
                        .param("questionType", "SINGLE_CHOICE")
                        .param("courseId", "2")
                        .param("difficulty", "3")
                        .param("questionId", "21")
                        .param("knowledgePointId", "31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(questionService).getEnabledQuestionPage(1, 5, "SINGLE_CHOICE", 2L, 3, 21L, 31L);
    }
}
