package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.AiLearningEffectVO;
import com.learnplatform.dto.AiUsageReportVO;
import com.learnplatform.service.AiLearningEffectService;
import com.learnplatform.service.AiUsageReportService;
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
    @Mock private AiUsageReportService aiUsageReportService;
    @Mock private AiLearningEffectService learningEffectService;

    @BeforeEach
    void setUp() {
        AdminAiUsageController controller = new AdminAiUsageController(
                aiUsageService, aiUsageReportService, learningEffectService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getReportUsesReportService() throws Exception {
        AiUsageReportVO report = new AiUsageReportVO();
        report.setDays(7);
        when(aiUsageReportService.getReport(7)).thenReturn(report);

        mockMvc.perform(get("/api/admin/ai-usage/report").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(7));
    }

    @Test
    void getLearningEffectReturnsObservationMetrics() throws Exception {
        AiLearningEffectVO vo = new AiLearningEffectVO();
        vo.setDays(30);
        vo.setMinimumComparisonSample(5L);
        vo.setMinimumDistinctUsers(3L);
        vo.setAfterViewPracticeCount(24L);
        vo.setAfterViewUserCount(8L);
        vo.setAfterViewCorrectRate(75.0);
        vo.setCorrectRateLift(8.5);
        vo.setVariantTrainingStartedCount(12L);
        vo.setVariantTrainingCompletedCount(9L);
        vo.setVariantTrainingCompletionRate(75.0);
        vo.setVariantTrainingAnsweredCount(8L);
        vo.setVariantTrainingCorrectCount(6L);
        vo.setVariantTrainingCorrectRate(75.0);
        vo.setVariantDifficultyMinimumSample(5L);
        vo.setVariantDifficultyCoveredCount(2L);
        vo.setVariantDifficultySufficientCount(1L);
        vo.setVariantDifficultyReadiness("INSUFFICIENT_DATA");
        AiLearningEffectVO.VariantDifficultyEffect difficulty = new AiLearningEffectVO.VariantDifficultyEffect();
        difficulty.setDifficulty(3);
        difficulty.setDifficultyLabel("中等");
        difficulty.setAnsweredCount(8L);
        difficulty.setAnsweredUserCount(6L);
        difficulty.setCorrectCount(6L);
        difficulty.setCorrectRate(75.0);
        difficulty.setSampleSufficient(true);
        vo.setVariantDifficultyStats(java.util.List.of(difficulty));
        vo.setCrossQuestionWindowDays(30);
        vo.setCrossQuestionAfterViewPracticeCount(18L);
        vo.setCrossQuestionAfterViewUserCount(7L);
        vo.setCrossQuestionAfterViewCorrectRate(72.2);
        vo.setCrossQuestionCorrectRateLift(6.7);
        vo.setCrossQuestionConclusionLevel("POSITIVE_ASSOCIATION");
        vo.setConclusionLevel("POSITIVE_ASSOCIATION");
        AiLearningEffectVO.AssetTypeEffect assetType = new AiLearningEffectVO.AssetTypeEffect();
        assetType.setAssetType("FULL_EXPLANATION");
        assetType.setAssetTypeLabel("标准解析");
        assetType.setAfterViewPracticeCount(12L);
        assetType.setAfterViewUserCount(5L);
        assetType.setAfterViewCorrectRate(75.0);
        assetType.setBaselinePracticeCount(10L);
        assetType.setBaselineUserCount(4L);
        assetType.setBaselineCorrectRate(60.0);
        assetType.setCorrectRateLift(15.0);
        assetType.setSampleSufficient(true);
        assetType.setConclusionLevel("POSITIVE_ASSOCIATION");
        vo.setAssetTypeStats(java.util.List.of(assetType));
        when(learningEffectService.getLearningEffect(30)).thenReturn(vo);

        mockMvc.perform(get("/api/admin/ai-usage/learning-effect").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days").value(30))
                .andExpect(jsonPath("$.data.minimumComparisonSample").value(5))
                .andExpect(jsonPath("$.data.minimumDistinctUsers").value(3))
                .andExpect(jsonPath("$.data.afterViewPracticeCount").value(24))
                .andExpect(jsonPath("$.data.afterViewUserCount").value(8))
                .andExpect(jsonPath("$.data.afterViewCorrectRate").value(75.0))
                .andExpect(jsonPath("$.data.correctRateLift").value(8.5))
                .andExpect(jsonPath("$.data.variantTrainingStartedCount").value(12))
                .andExpect(jsonPath("$.data.variantTrainingCompletedCount").value(9))
                .andExpect(jsonPath("$.data.variantTrainingCompletionRate").value(75.0))
                .andExpect(jsonPath("$.data.variantTrainingAnsweredCount").value(8))
                .andExpect(jsonPath("$.data.variantTrainingCorrectCount").value(6))
                .andExpect(jsonPath("$.data.variantTrainingCorrectRate").value(75.0))
                .andExpect(jsonPath("$.data.variantDifficultyMinimumSample").value(5))
                .andExpect(jsonPath("$.data.variantDifficultyCoveredCount").value(2))
                .andExpect(jsonPath("$.data.variantDifficultyReadiness").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.data.variantDifficultyStats[0].difficultyLabel").value("中等"))
                .andExpect(jsonPath("$.data.variantDifficultyStats[0].answeredUserCount").value(6))
                .andExpect(jsonPath("$.data.variantDifficultyStats[0].sampleSufficient").value(true))
                .andExpect(jsonPath("$.data.crossQuestionWindowDays").value(30))
                .andExpect(jsonPath("$.data.crossQuestionAfterViewPracticeCount").value(18))
                .andExpect(jsonPath("$.data.crossQuestionAfterViewUserCount").value(7))
                .andExpect(jsonPath("$.data.crossQuestionAfterViewCorrectRate").value(72.2))
                .andExpect(jsonPath("$.data.crossQuestionCorrectRateLift").value(6.7))
                .andExpect(jsonPath("$.data.crossQuestionConclusionLevel").value("POSITIVE_ASSOCIATION"))
                .andExpect(jsonPath("$.data.assetTypeStats[0].afterViewPracticeCount").value(12))
                .andExpect(jsonPath("$.data.assetTypeStats[0].afterViewUserCount").value(5))
                .andExpect(jsonPath("$.data.assetTypeStats[0].baselineCorrectRate").value(60.0))
                .andExpect(jsonPath("$.data.assetTypeStats[0].correctRateLift").value(15.0))
                .andExpect(jsonPath("$.data.assetTypeStats[0].sampleSufficient").value(true))
                .andExpect(jsonPath("$.data.assetTypeStats[0].conclusionLevel").value("POSITIVE_ASSOCIATION"))
                .andExpect(jsonPath("$.data.conclusionLevel").value("POSITIVE_ASSOCIATION"));
    }
}
