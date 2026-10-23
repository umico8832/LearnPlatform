package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.config.AiExecutionProperties;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiAssetEngagementService;
import com.learnplatform.service.AiCallGovernanceService;
import com.learnplatform.service.AiService;
import com.learnplatform.service.ExamLearningAiService;
import com.learnplatform.service.KnowledgeGraphService;
import com.learnplatform.service.LearningDiagnosisService;
import com.learnplatform.service.LearningPathService;
import com.learnplatform.service.LearningReportService;
import com.learnplatform.service.QuestionLearningAssetService;
import com.learnplatform.service.ReviewScheduleQueryService;
import com.learnplatform.service.SpacedRepetitionService;
import com.learnplatform.service.StatisticsService;
import com.learnplatform.service.ai.AiTaskExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiCapacityExceptionHandlerTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings = {"/api/ai/explanation/stream", "/api/review/ai-suggestion/stream",
            "/api/statistics/ai-advice/stream", "/api/exam/learning-sessions/1/questions/1/ai/explanation/stream"})
    void allSharedExecutorCallersRejectWithJsonWithoutCallingTheModel(String path) throws Exception {
        var ai = mock(AiService.class);
        var diagnosis = mock(LearningDiagnosisService.class);
        var exam = mock(ExamLearningAiService.class);
        try (var executor = new AiTaskExecutor(new AiExecutionProperties(1, AiExecutionProperties.Mode.PLATFORM), new SimpleMeterRegistry()); var occupied = executor.reserve()) {
            org.junit.jupiter.api.Assertions.assertNotNull(occupied);
            var mvc = MockMvcBuilders.standaloneSetup(
                    new AiController(ai, mock(AiCallGovernanceService.class),
                            mock(QuestionLearningAssetService.class), mock(AiAssetEngagementService.class), executor),
                    new ReviewController(mock(SpacedRepetitionService.class),
                            mock(ReviewScheduleQueryService.class), ai, executor),
                    new StatisticsController(mock(StatisticsService.class), mock(LearningReportService.class),
                            mock(LearningPathService.class), mock(KnowledgeGraphService.class), diagnosis, executor),
                    new ExamLearningAiController(exam, executor))
                    .setControllerAdvice(new AiCapacityExceptionHandler(), new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver()).build();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new CustomUserDetails(7L, "learner", "USER"), null, List.of()));
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                            .content("{\"questionId\":1}"))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(1007));
            verifyNoInteractions(ai, diagnosis, exam);
        }
    }
}
