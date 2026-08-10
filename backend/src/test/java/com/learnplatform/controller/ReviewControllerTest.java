package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiService;
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
    @Mock private AiService aiService;
    @Mock private Executor aiTaskExecutor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReviewController controller = new ReviewController(spacedRepetitionService, aiService, aiTaskExecutor);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    @Test
    void dueCardsForwardCourseAndServerSelectedQuestion() throws Exception {
        ReviewScheduleVO card = new ReviewScheduleVO();
        card.setQuestionId(21L);
        when(spacedRepetitionService.getDueReviewCards(7L, 10L, 21L, 30))
                .thenReturn(List.of(card));

        mockMvc.perform(get("/api/review/due")
                        .with(mockUser(7L))
                        .param("courseId", "10")
                        .param("questionId", "21")
                        .param("limit", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].questionId").value(21));

        verify(spacedRepetitionService).getDueReviewCards(7L, 10L, 21L, 30);
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
