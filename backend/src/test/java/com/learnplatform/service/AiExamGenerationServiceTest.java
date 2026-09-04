package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.entity.Question;
import com.learnplatform.service.AiExamGenerationService.SmartExamPreview;
import com.learnplatform.service.AiExamGenerationService.SmartExamRequest;
import com.learnplatform.service.exam.AiExamCandidateLoader;
import com.learnplatform.service.exam.AiExamPaperCreationService;
import com.learnplatform.service.exam.AiExamPreviewPresentationService;
import com.learnplatform.service.exam.AiExamQuestionSelectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiExamGenerationServiceTest {

    @Mock private AiExamCandidateLoader candidateLoader;
    @Mock private AiExamQuestionSelectionService questionSelectionService;
    @Mock private AiExamPreviewPresentationService previewPresentationService;
    @Mock private AiExamPaperCreationService paperCreationService;

    private AiExamGenerationService service;

    @BeforeEach
    void setUp() {
        service = new AiExamGenerationService(candidateLoader, questionSelectionService,
                previewPresentationService, paperCreationService);
    }

    @Test
    void previewRejectsInvalidCountBeforeLoadingCandidates() {
        SmartExamRequest request = new SmartExamRequest();
        request.setQuestionCount(101);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(request, 1L));

        assertEquals("题目数量应在 1-100 之间", exception.getMessage());
        verify(candidateLoader, never()).loadAvailableQuestions(any());
    }

    @Test
    void previewUsesUserLearningFactsOnlyWhenTheExistingRulesRequireThem() {
        SmartExamRequest request = new SmartExamRequest();
        request.setCourseId(10L);
        request.setQuestionCount(1);
        Question question = new Question();
        question.setId(100L);
        SmartExamPreview expected = new SmartExamPreview();
        expected.setQuestionIds(List.of(100L));
        when(candidateLoader.loadAvailableQuestions(10L)).thenReturn(List.of(question));
        when(candidateLoader.loadQuestionKnowledgePoints(List.of(question))).thenReturn(Map.of());
        when(candidateLoader.loadKnowledgePointNames()).thenReturn(Map.of());
        when(candidateLoader.loadUserWrongQuestionIds(1L)).thenReturn(Set.of(100L));
        when(candidateLoader.loadUserDifficultyAccuracy(1L)).thenReturn(Map.of(3, 0.4));
        when(questionSelectionService.select(eq(List.of(question)), eq(Map.of()), eq(Set.of(100L)),
                eq(Map.of(3, 0.4)), eq(request), eq(1))).thenReturn(List.of(100L));
        when(previewPresentationService.create(eq(request), eq(List.of(question)), eq(List.of(100L)),
                eq(Map.of()), eq(Map.of()), eq(Set.of(100L)), eq(Map.of(3, 0.4)))).thenReturn(expected);

        SmartExamPreview result = service.preview(request, 1L);

        assertEquals(expected, result);
        verify(candidateLoader).loadUserWrongQuestionIds(1L);
        verify(candidateLoader).loadUserDifficultyAccuracy(1L);
    }

    @Test
    void createSmartExamKeepsPublicCreationEntryPoint() {
        SmartExamPreview preview = new SmartExamPreview();
        ExamPaperVO expected = new ExamPaperVO();
        expected.setId(9L);
        when(paperCreationService.create(preview, 7L)).thenReturn(expected);

        ExamPaperVO result = service.createSmartExam(preview, 7L);

        assertEquals(expected, result);
        verify(paperCreationService).create(preview, 7L);
    }
}
