package com.learnplatform.service.understanding;

import tools.jackson.databind.json.JsonMapper;
import com.learnplatform.dto.PracticeResultVO;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.QuestionUnderstandingMapper;
import com.learnplatform.service.CourseLearningEventService;
import com.learnplatform.service.PracticeAnswerService;
import com.learnplatform.service.review.FsrsReviewService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionUnderstandingReviewResultTest {
    @Test void originalResultUsesPersistedScheduleAndSourceReceiptInsteadOfGuessingFromCorrectness() {
        var content = mock(QuestionUnderstandingContent.class);
        var mapper = mock(QuestionUnderstandingMapper.class);
        var practice = mock(PracticeAnswerService.class);
        var fsrs = mock(FsrsReviewService.class);
        var question = new Question();
        question.setId(9L);
        var ready = new QuestionUnderstandingContent.Ready(question, Map.of("id", 12L), java.util.List.of(), Map.of());
        when(content.ready(7L, 9L, null, false)).thenReturn(ready);
        var request = JsonMapper.builderWithJackson2Defaults().build().createObjectNode().put("answer", "A");
        when(content.answer(request, ready.options(), false)).thenReturn(new QuestionUnderstandingContent.Answer("A", null, 1L));
        var practiceResult = new PracticeResultVO();
        practiceResult.setRecordId(22L);
        practiceResult.setCorrect(true);
        when(practice.submitAnswer(any(), eq(7L))).thenReturn(practiceResult);
        var summary = Map.<String, Object>of("nextReviewDate", LocalDate.of(2030, 3, 4), "counted", true);
        when(mapper.review(7L, 9L, "PRACTICE", 22L)).thenReturn(summary);
        var service = new QuestionUnderstandingService(content, mapper, practice,
                mock(CourseLearningEventService.class), mock(QuestionTutorService.class),
                mock(QuestionUnderstandingPresentation.class), fsrs,
                mock(com.learnplatform.service.ContentReferenceLockService.class));
        var result = service.submit(7L, 9L, request);
        assertEquals(summary, result.get("review"));
        verify(fsrs).score(7L, 9L, FsrsReviewService.Source.PRACTICE, 22L, true, null);
        verify(mapper).review(7L, 9L, "PRACTICE", 22L);
    }
}
