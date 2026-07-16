package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.AiVariantTrainingVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiLearningEffectService;
import com.learnplatform.service.AiService;
import com.learnplatform.service.QuestionLearningAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiAssetViewControllerTest {

    private MockMvc mockMvc;

    @Mock private AiService aiService;
    @Mock private QuestionLearningAssetService learningAssetService;
    @Mock private AiLearningEffectService learningEffectService;

    @BeforeEach
    void setUp() {
        AiController controller = new AiController(
                aiService, learningAssetService, learningEffectService, Runnable::run);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    @Test
    void recordAssetViewUsesAuthenticatedUser() throws Exception {
        AiVariantTrainingVO training = new AiVariantTrainingVO();
        training.setQuestionId(42L);
        training.setAssetId(9L);
        training.setStatus("STARTED");
        training.setCompleted(false);
        when(learningEffectService.recordAssetView(42L, AiAssetType.VARIANT, 7L)).thenReturn(training);

        mockMvc.perform(post("/api/ai/asset/view")
                        .with(mockUser(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":42,\"assetType\":\"VARIANT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("STARTED"))
                .andExpect(jsonPath("$.data.completed").value(false));

        verify(learningEffectService).recordAssetView(42L, AiAssetType.VARIANT, 7L);
    }

    @Test
    void completeVariantTrainingUsesAuthenticatedUser() throws Exception {
        AiVariantTrainingVO training = new AiVariantTrainingVO();
        training.setQuestionId(42L);
        training.setStatus("COMPLETED");
        training.setCompleted(true);
        when(learningEffectService.completeVariantTraining(42L, 7L)).thenReturn(training);

        mockMvc.perform(post("/api/ai/variant-training/42/complete").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));

        verify(learningEffectService).completeVariantTraining(42L, 7L);
    }

    @Test
    void submitVariantAnswerUsesAuthenticatedUser() throws Exception {
        AiVariantTrainingVO training = new AiVariantTrainingVO();
        training.setQuestionId(42L);
        training.setStatus("COMPLETED");
        training.setCompleted(true);
        training.setAnswered(true);
        training.setCorrect(true);
        when(learningEffectService.submitVariantAnswer(42L, 7L, "B")).thenReturn(training);

        mockMvc.perform(post("/api/ai/variant-training/42/answer")
                        .with(mockUser(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userAnswer\":\"B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answered").value(true))
                .andExpect(jsonPath("$.data.correct").value(true));

        verify(learningEffectService).submitVariantAnswer(42L, 7L, "B");
    }

    @Test
    void recordAssetViewRejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/ai/asset/view")
                        .with(mockUser(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(learningEffectService);
    }

    private RequestPostProcessor mockUser(Long userId) {
        return request -> {
            CustomUserDetails details = new CustomUserDetails(userId, "testuser", "USER");
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    details, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            return request;
        };
    }
}
