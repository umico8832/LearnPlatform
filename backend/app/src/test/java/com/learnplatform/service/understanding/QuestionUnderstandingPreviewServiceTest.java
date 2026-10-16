package com.learnplatform.service.understanding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.mapper.QuestionTutorMapper;
import com.learnplatform.mapper.QuestionUnderstandingMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class QuestionUnderstandingPreviewServiceTest {
    @Test void deniesNonAdminBeforeReadingContentOrValidatingProgress() {
        var content = mock(QuestionUnderstandingContent.class);
        var mapper = mock(QuestionUnderstandingMapper.class);
        var hints = mock(QuestionTutorMapper.class);
        var presentation = mock(QuestionUnderstandingPresentation.class);
        var tutor = mock(QuestionTutorService.class);
        var service = new QuestionUnderstandingPreviewService(content, mapper, hints, presentation, tutor);
        for (String role : new String[] {"USER", "TEACHER", null}) {
            var error = assertThrows(BusinessException.class, () -> service.preview(7L, role, -1L, null));
            assertEquals(1004, error.getCode());
        }
        verifyNoInteractions(content, mapper, hints, presentation, tutor);
    }

    @Test void rejectsForgedAttemptIdsAndInvalidProgressBeforeReadingContent() throws Exception {
        var content = mock(QuestionUnderstandingContent.class);
        var service = new QuestionUnderstandingPreviewService(content, null, null, null, null);
        var json = new ObjectMapper();
        for (String body : new String[] {"[]", "{\"attemptId\":7}", "{\"assistance\":\"UNAIDED\"}",
                "{\"hintLevel\":4}", "{\"hintLevel\":1.5}", "{\"revealSolution\":\"true\"}"}) {
            var node = json.readTree(body);
            assertEquals(1001, assertThrows(BusinessException.class,
                    () -> service.preview(7L, "ADMIN", 9L, node)).getCode());
        }
        verifyNoInteractions(content);
    }
}
