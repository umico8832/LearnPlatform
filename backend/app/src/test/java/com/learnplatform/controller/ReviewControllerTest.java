package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.dto.ReviewStatsVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiService;
import com.learnplatform.service.ReviewScheduleQueryService;
import com.learnplatform.service.SpacedRepetitionService;
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
import java.util.concurrent.Executor;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock private SpacedRepetitionService spacedRepetitionService;
    @Mock private ReviewScheduleQueryService reviewScheduleQueryService;
    @Mock private AiService aiService;
    @Mock private Executor aiTaskExecutor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReviewController controller = new ReviewController(
                spacedRepetitionService, reviewScheduleQueryService, aiService, aiTaskExecutor);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    @Test
    void dueCardsForwardCourseAndServerSelectedQuestion() throws Exception {
        ReviewScheduleVO card = new ReviewScheduleVO();
        card.setQuestionId(21L);
        when(reviewScheduleQueryService.getDueReviewCards(7L, 10L, 21L, null, 30))
                .thenReturn(List.of(card));

        mockMvc.perform(get("/api/review/due")
                        .with(mockUser(7L))
                        .param("courseId", "10")
                        .param("questionId", "21")
                        .param("limit", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].questionId").value(21));

        verify(reviewScheduleQueryService).getDueReviewCards(7L, 10L, 21L, null, 30);
    }

    @Test
    void dueCardsForwardKnowledgePointFilter() throws Exception {
        ReviewScheduleVO card = new ReviewScheduleVO();
        card.setQuestionId(101L);
        when(reviewScheduleQueryService.getDueReviewCards(7L, 10L, null, 31L, 30))
                .thenReturn(List.of(card));

        mockMvc.perform(get("/api/review/due")
                        .with(mockUser(7L))
                        .param("courseId", "10")
                        .param("knowledgePointId", "31")
                        .param("limit", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].questionId").value(101));

        verify(reviewScheduleQueryService).getDueReviewCards(7L, 10L, null, 31L, 30);
    }

    @Test
    void statsAreDelegatedToReadOnlyQueryService() throws Exception {
        ReviewStatsVO stats = new ReviewStatsVO();
        stats.setTotalCards(4);
        when(reviewScheduleQueryService.getReviewStats(7L)).thenReturn(stats);

        mockMvc.perform(get("/api/review/stats").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCards").value(4));

        verify(reviewScheduleQueryService).getReviewStats(7L);
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
