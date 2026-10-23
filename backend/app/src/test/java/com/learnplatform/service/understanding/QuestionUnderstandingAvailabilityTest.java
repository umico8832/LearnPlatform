package com.learnplatform.service.understanding;

import tools.jackson.databind.json.JsonMapper;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionUnderstandingMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionUnderstandingAvailabilityTest {
    @Test void draftContentCannotCreateAListEntryEvenWhenQuestionIsPublic() {
        var questions = mock(QuestionMapper.class);
        var mapper = mock(QuestionUnderstandingMapper.class);
        var question = new Question();
        question.setId(9L);
        question.setStatus(1);
        question.setVisibility("PUBLIC");
        question.setQuestionType("SINGLE_CHOICE");
        when(questions.selectById(9L)).thenReturn(question);
        when(mapper.latestContent(9L)).thenReturn(Map.of("review_status", "PENDING"));
        var content = new QuestionUnderstandingContent(questions, mapper, JsonMapper.builderWithJackson2Defaults().build());
        assertFalse(content.available(7L, 9L));
        verify(mapper, never()).options(anyLong());
    }

    @Test void infrastructureFailuresArePropagatedRatherThanReportedAsMissingContent() {
        var questions = mock(QuestionMapper.class);
        when(questions.selectById(9L)).thenThrow(new DataAccessResourceFailureException("database unavailable"));
        var content = new QuestionUnderstandingContent(questions, mock(QuestionUnderstandingMapper.class), JsonMapper.builderWithJackson2Defaults().build());
        assertThrows(DataAccessResourceFailureException.class, () -> content.available(7L, 9L));
    }
}
