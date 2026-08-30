package com.learnplatform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.PracticeResultVO;
import com.learnplatform.dto.PracticeSubmitRequest;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AdaptivePracticeService;
import com.learnplatform.service.PracticeService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PracticeController MockMvc 集成测试（standalone 模式）
 */
@ExtendWith(MockitoExtension.class)
class PracticeControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PracticeService practiceService;

    @Mock
    private AdaptivePracticeService adaptivePracticeService;

    @InjectMocks
    private PracticeController practiceController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(practiceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    private RequestPostProcessor mockUser(Long userId) {
        // 直接设置 SecurityContextHolder ThreadLocal，绕过 standalone MockMvc 无安全过滤器的问题
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

    // ======================== 获取练习题目 ========================

    @Test
    void getPracticeQuestions_defaultParams() throws Exception {
        QuestionVO q = new QuestionVO();
        q.setId(1L);
        q.setContent("Java 中 String 是引用类型吗？");

        when(practiceService.getPracticeQuestions(isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(q));

        mockMvc.perform(get("/api/practice/questions").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].content").value("Java 中 String 是引用类型吗？"));
    }

    @Test
    void getPracticeQuestions_withFilters() throws Exception {
        when(practiceService.getPracticeQuestions(eq(1L), isNull(), eq("SINGLE_CHOICE"), eq(2), eq(5)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/practice/questions")
                        .with(mockUser(1L))
                        .param("courseId", "1")
                        .param("questionType", "SINGLE_CHOICE")
                        .param("difficulty", "2")
                        .param("count", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ======================== 提交答案 ========================

    @Test
    void submitAnswer_correct() throws Exception {
        PracticeResultVO result = new PracticeResultVO();
        result.setCorrect(true);
        result.setCorrectAnswer("A");

        when(practiceService.submitAnswer(any(PracticeSubmitRequest.class), eq(7L)))
                .thenReturn(result);

        PracticeSubmitRequest req = new PracticeSubmitRequest();
        req.setQuestionId(1L);
        req.setUserAnswer("A");
        req.setAnswerTime(30);

        mockMvc.perform(post("/api/practice/submit")
                        .with(mockUser(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.correct").value(true));
    }

    @Test
    void submitAnswer_missingQuestionId_returns400() throws Exception {
        PracticeSubmitRequest req = new PracticeSubmitRequest();
        req.setUserAnswer("A");
        // questionId is null

        mockMvc.perform(post("/api/practice/submit")
                        .with(mockUser(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void submitAnswer_blankAnswer_returns400() throws Exception {
        PracticeSubmitRequest req = new PracticeSubmitRequest();
        req.setQuestionId(1L);
        req.setUserAnswer("");

        mockMvc.perform(post("/api/practice/submit")
                        .with(mockUser(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    // ======================== 练习统计 ========================

    @Test
    void getPracticeStats_returnsStats() throws Exception {
        when(practiceService.getUserPracticeStats(1L))
                .thenReturn(Map.of("totalPractice", 100, "correctRate", 0.85));

        mockMvc.perform(get("/api/practice/stats").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalPractice").value(100))
                .andExpect(jsonPath("$.data.correctRate").value(0.85));
    }

    // ======================== 错题重练 ========================

    @Test
    void getWrongQuestionPractice_defaultParams() throws Exception {
        when(practiceService.getWrongQuestionPractice(eq(1L), isNull(), isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/practice/wrong-questions").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getWrongQuestionPractice_withParams() throws Exception {
        QuestionVO q = new QuestionVO();
        q.setId(5L);
        when(practiceService.getWrongQuestionPractice(eq(1L), eq(1), eq(3)))
                .thenReturn(List.of(q));

        mockMvc.perform(get("/api/practice/wrong-questions")
                        .with(mockUser(1L))
                        .param("masteryLevel", "1")
                        .param("count", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ======================== 收藏题练习 ========================

    @Test
    void getFavoritePractice_returnsQuestions() throws Exception {
        when(practiceService.getFavoritePractice(eq(1L), isNull(), isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/practice/favorites").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ======================== 自适应推荐 ========================

    @Test
    void getAdaptiveQuestions_returnsQuestions() throws Exception {
        when(adaptivePracticeService.getAdaptiveQuestions(eq(1L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/practice/adaptive").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void getAdaptiveSummary_returnsSummary() throws Exception {
        when(adaptivePracticeService.getAdaptiveSummary(1L))
                .thenReturn(Map.of("difficultyWeights", Map.of(), "recommendLevel", 2));

        mockMvc.perform(get("/api/practice/adaptive/summary").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recommendLevel").value(2));
    }
}
