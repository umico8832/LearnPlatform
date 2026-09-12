package com.learnplatform.controller;

import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.QuestionSubmissionImportService;
import com.learnplatform.service.QuestionSubmissionService;
import com.learnplatform.service.SubmissionAiQualityService;
import com.learnplatform.service.SubmissionDifficultyAssessmentService;
import com.learnplatform.service.SubmissionKPTaggingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminQuestionSubmissionControllerTest {

    @Mock private QuestionSubmissionService submissionService;
    @Mock private QuestionSubmissionImportService submissionImportService;
    @Mock private SubmissionAiQualityService qualityService;
    @Mock private SubmissionKPTaggingService kpTaggingService;
    @Mock private SubmissionDifficultyAssessmentService difficultyAssessmentService;

    @InjectMocks
    private AdminQuestionSubmissionController controller;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void importEndpointUsesDedicatedImportService() {
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(1L, "admin", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(submissionImportService.importSubmission(10L, 1L)).thenReturn(new QuestionSubmissionVO());

        controller.importToQuestionBank(10L);

        verify(submissionImportService).importSubmission(10L, 1L);
    }
}
