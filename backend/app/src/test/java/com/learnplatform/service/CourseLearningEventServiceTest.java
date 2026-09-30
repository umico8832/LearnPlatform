package com.learnplatform.service;

import com.learnplatform.entity.CourseLearningEvent;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.CourseLearningEventMapper;
import com.learnplatform.mapper.UserCourseMapper;
import com.learnplatform.service.gamification.GamificationRewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseLearningEventServiceTest {

    @Mock private UserCourseMapper userCourseMapper;
    @Mock private CourseLearningEventMapper courseLearningEventMapper;

    private CourseLearningEventService service;

    @BeforeEach
    void setUp() {
        service = new CourseLearningEventService(userCourseMapper, courseLearningEventMapper,
                org.mockito.Mockito.mock(com.learnplatform.service.gamification.GamificationRewardService.class));
    }

    @Test
    void recordsVersionedEventForQuestionInUsersCourseLibrary() {
        Question question = new Question();
        question.setId(12L);
        question.setCourseId(8L);
        when(userCourseMapper.selectCount(any())).thenReturn(1L);
        LocalDateTime occurredTime = LocalDateTime.of(2026, 8, 3, 12, 0);

        service.recordQuestionAnswer(7L, question, "PRACTICE_ANSWERED", "PRACTICE", 32L, false, occurredTime);

        ArgumentCaptor<CourseLearningEvent> captor = ArgumentCaptor.forClass(CourseLearningEvent.class);
        verify(courseLearningEventMapper).insert(captor.capture());
        CourseLearningEvent event = captor.getValue();
        assertEquals(7L, event.getUserId());
        assertEquals(8L, event.getCourseId());
        assertEquals("PRACTICE:32", event.getIdempotencyKey());
        assertEquals(1, event.getEventVersion());
        assertEquals("{\"isCorrect\":false}", event.getPayloadJson());
        assertEquals(occurredTime, event.getOccurredTime());
    }

    @Test
    void doesNotTreatPracticeOutsideCourseLibraryAsCourseLearning() {
        Question question = new Question();
        question.setId(12L);
        question.setCourseId(8L);
        when(userCourseMapper.selectCount(any())).thenReturn(0L);

        service.recordQuestionAnswer(7L, question, "PRACTICE_ANSWERED", "PRACTICE", 32L, true, null);

        verify(courseLearningEventMapper, never()).insert(any());
    }

    @Test
    void projectsOnlyInsertedCourseFactsToTheRewardLedger() {
        GamificationRewardService rewards = org.mockito.Mockito.mock(GamificationRewardService.class);
        CourseLearningEventService rewardedService = new CourseLearningEventService(
                userCourseMapper, courseLearningEventMapper, rewards);
        Question question = new Question();
        question.setId(12L);
        question.setCourseId(8L);
        when(userCourseMapper.selectCount(any())).thenReturn(1L);

        rewardedService.recordQuestionAnswer(7L, question, "PRACTICE_ANSWERED", "PRACTICE", 32L, true, null);

        verify(rewards).reward(any(CourseLearningEvent.class), org.mockito.ArgumentMatchers.eq(true));
    }
}
