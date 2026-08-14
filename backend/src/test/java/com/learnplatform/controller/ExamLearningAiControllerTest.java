package com.learnplatform.controller;

import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamLearningAiService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExamLearningAiControllerTest {

    @Test
    void streamsAssistanceWithAuthenticatedUserAndRouteContext() {
        ExamLearningAiService service = mock(ExamLearningAiService.class);
        Executor directExecutor = Runnable::run;
        ExamLearningAiController controller = new ExamLearningAiController(service, directExecutor);

        ResponseEntity<SseEmitter> response = controller.streamAssistance(
                30L, 10L, "explanation", new CustomUserDetails(7L, "learner", "USER"));

        assertNotNull(response.getBody());
        verify(service).streamAssistance(
                org.mockito.ArgumentMatchers.eq(30L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("explanation"),
                org.mockito.ArgumentMatchers.eq(7L), any());
    }
}
