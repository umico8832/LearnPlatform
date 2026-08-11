package com.learnplatform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.ExamLearningAnswerRequest;
import com.learnplatform.dto.ExamLearningAnswerResultVO;
import com.learnplatform.dto.ExamLearningSessionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamPaperLearningService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExamLearningControllerTest {

    @Mock private ExamService examService;
    @Mock private ExamPaperService examPaperService;
    @Mock private ExamPaperLearningService learningService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ExamController controller = new ExamController(examService, examPaperService, learningService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    @Test
    void startsAndReadsAuthenticatedLearningSession() throws Exception {
        ExamLearningSessionVO session = new ExamLearningSessionVO();
        session.setId(30L);
        session.setExamPaperId(2L);
        when(learningService.startSession(2L, 7L)).thenReturn(session);
        when(learningService.getSession(30L, 7L)).thenReturn(session);

        mockMvc.perform(post("/api/exam/papers/2/learning-sessions").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(30));
        mockMvc.perform(get("/api/exam/learning-sessions/30").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examPaperId").value(2));

        verify(learningService).startSession(2L, 7L);
        verify(learningService).getSession(30L, 7L);
    }

    @Test
    void submitsOneAnswerAndCompletesAuthenticatedSession() throws Exception {
        ExamLearningAnswerResultVO result = new ExamLearningAnswerResultVO();
        result.setQuestionId(10L);
        result.setCorrect(true);
        when(learningService.submitAnswer(
                org.mockito.ArgumentMatchers.eq(30L),
                org.mockito.ArgumentMatchers.any(ExamLearningAnswerRequest.class),
                org.mockito.ArgumentMatchers.eq(7L))).thenReturn(result);
        ExamLearningSessionVO completed = new ExamLearningSessionVO();
        completed.setId(30L);
        completed.setStatus(1);
        when(learningService.completeSession(30L, 7L)).thenReturn(completed);

        ExamLearningAnswerRequest request = new ExamLearningAnswerRequest();
        request.setQuestionId(10L);
        request.setUserAnswer("A");
        mockMvc.perform(post("/api/exam/learning-sessions/30/answers")
                        .with(mockUser(7L))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true));
        mockMvc.perform(post("/api/exam/learning-sessions/30/complete").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));

        verify(learningService).completeSession(30L, 7L);
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
