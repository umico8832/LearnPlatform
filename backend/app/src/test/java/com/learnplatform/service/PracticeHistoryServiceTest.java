package com.learnplatform.service;

import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeHistoryServiceTest {
    @Mock private PracticeRecordMapper records;
    @Mock private QuestionMapper questions;
    @Mock private CourseMapper courses;

    @Test
    void statsUsesOneDatabaseAggregateWithoutMaterializingPracticeFacts() {
        when(records.aggregateVisibleStats(7L)).thenReturn(new PracticeRecordMapper.PracticeStats(7_500, 4_321));
        var service = new PracticeHistoryService(records, questions, courses);

        Map<String, Object> stats = service.getUserPracticeStats(7L);

        assertEquals(7_500, stats.get("totalAnswered"));
        assertEquals(4_321, stats.get("correctCount"));
        assertEquals(3_179, stats.get("wrongCount"));
        assertEquals(57.6, stats.get("correctRate"));
        verify(records).aggregateVisibleStats(7L);
        verify(records, never()).selectList(any());
        verifyNoInteractions(questions, courses);
    }
}
