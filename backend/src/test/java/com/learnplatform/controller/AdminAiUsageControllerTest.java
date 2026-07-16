package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.AiLearningEffectVO;
import com.learnplatform.service.AiLearningEffectService;
import com.learnplatform.service.AiUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAiUsageControllerTest {

    private MockMvc mockMvc;

    @Mock private AiUsageService aiUsageService;
    @Mock private AiLearningEffectService learningEffectService;

    @BeforeEach
    void setUp() {
        AdminAiUsageController controller = new AdminAiUsageController(aiUsageService, learningEffectService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getLearningEffectReturnsObservationMetrics() throws Exception {
        AiLearningEffectVO vo = new AiLearningEffectVO();
        vo.setDays(30);
        vo.setAfterViewPracticeCount(24L);
        vo.setAfterViewCorrectRate(75.0);
        vo.setCorrectRateLift(8.5);
        vo.setVariantTrainingStartedCount(12L);
        vo.setVariantTrainingCompletedCount(9L);
        vo.setVariantTrainingCompletionRate(75.0);
        vo.setConclusionLevel("POSITIVE_ASSOCIATION");
        when(learningEffectService.getLearningEffect(30)).thenReturn(vo);

        mockMvc.perform(get("/api/admin/ai-usage/learning-effect").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days").value(30))
                .andExpect(jsonPath("$.data.afterViewPracticeCount").value(24))
                .andExpect(jsonPath("$.data.afterViewCorrectRate").value(75.0))
                .andExpect(jsonPath("$.data.correctRateLift").value(8.5))
                .andExpect(jsonPath("$.data.variantTrainingStartedCount").value(12))
                .andExpect(jsonPath("$.data.variantTrainingCompletedCount").value(9))
                .andExpect(jsonPath("$.data.variantTrainingCompletionRate").value(75.0))
                .andExpect(jsonPath("$.data.conclusionLevel").value("POSITIVE_ASSOCIATION"));
    }
}
