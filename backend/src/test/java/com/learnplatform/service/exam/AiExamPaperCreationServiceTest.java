package com.learnplatform.service.exam;

import com.learnplatform.dto.ExamPaperCreateRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.service.AiExamGenerationService.SmartExamPreview;
import com.learnplatform.service.ExamPaperService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiExamPaperCreationServiceTest {

    @Mock private ExamPaperService examPaperService;

    @Test
    void createKeepsDraftStatusQuestionOrderAndUnitScores() {
        AiExamPaperCreationService service = new AiExamPaperCreationService(examPaperService);
        SmartExamPreview preview = new SmartExamPreview();
        preview.setTitle("智能试卷");
        preview.setDescription("说明");
        preview.setCourseId(3L);
        preview.setDuration(45);
        preview.setQuestionIds(List.of(11L, 12L));
        ExamPaperVO expected = new ExamPaperVO();
        when(examPaperService.createExamPaper(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
                .thenReturn(expected);

        assertEquals(expected, service.create(preview, 9L));

        ArgumentCaptor<ExamPaperCreateRequest> captor = ArgumentCaptor.forClass(ExamPaperCreateRequest.class);
        verify(examPaperService).createExamPaper(captor.capture(), org.mockito.ArgumentMatchers.eq(9L));
        ExamPaperCreateRequest request = captor.getValue();
        assertEquals(0, request.getStatus());
        assertEquals(List.of(11L, 12L), request.getQuestions().stream()
                .map(ExamPaperCreateRequest.QuestionItem::getQuestionId).toList());
        assertEquals(List.of(1, 2), request.getQuestions().stream()
                .map(ExamPaperCreateRequest.QuestionItem::getSortOrder).toList());
        assertEquals(List.of(1, 1), request.getQuestions().stream()
                .map(ExamPaperCreateRequest.QuestionItem::getScore).toList());
    }
}
