package com.learnplatform.controller;

import com.learnplatform.dto.AiAssetFeedbackVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiLearningEffectService;
import com.learnplatform.service.AiService;
import com.learnplatform.service.QuestionLearningAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiService aiService;
    @Mock
    private QuestionLearningAssetService learningAssetService;
    @Mock
    private AiLearningEffectService learningEffectService;
    @Mock
    private Executor aiTaskExecutor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserDetails(7L, "learner", "USER"),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        AiController controller = new AiController(
                aiService, learningAssetService, learningEffectService, aiTaskExecutor);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    @Test
    void getAssetFeedbackReturnsTypedVoWithoutEntityExposure() throws Exception {
        when(learningAssetService.getUserFeedback(10L, "FULL_EXPLANATION", 7L))
                .thenReturn(new AiAssetFeedbackVO(true, "有帮助"));

        mockMvc.perform(get("/api/ai/asset/feedback/10/FULL_EXPLANATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helpful").value(true))
                .andExpect(jsonPath("$.data.comment").value("有帮助"));
    }
}
