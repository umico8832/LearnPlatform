package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.AiAssetType;
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
        mockMvc.perform(post("/api/ai/asset/view")
                        .with(mockUser(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":42,\"assetType\":\"STEP_BY_STEP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(learningEffectService).recordAssetView(42L, AiAssetType.STEP_BY_STEP, 7L);
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
