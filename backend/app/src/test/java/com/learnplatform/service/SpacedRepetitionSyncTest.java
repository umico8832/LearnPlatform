package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SpacedRepetitionService.syncWrongQuestionsToReviewPlan 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SpacedRepetitionSyncTest {

    @Mock private QuestionReviewScheduleMapper reviewScheduleMapper;
    @Mock private WrongQuestionMapper wrongQuestionMapper;
    private SpacedRepetitionService service;

    @BeforeEach
    void setUp() {
        service = new SpacedRepetitionService(
                reviewScheduleMapper, wrongQuestionMapper, null, null, null
        );
    }

    @Test
    void sync_wrongQuestions_empty_returnsZero() {
        when(wrongQuestionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        int result = service.syncWrongQuestionsToReviewPlan(1L);
        assertEquals(0, result);
        verify(reviewScheduleMapper, never()).insert(any());
    }

    @Test
    void sync_allAlreadyInPlan_returnsZero() {
        WrongQuestion wq = wrongQuestion(1L, 100L, 0);
        when(wrongQuestionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(wq));

        QuestionReviewSchedule existing = new QuestionReviewSchedule();
        existing.setQuestionId(100L);
        when(reviewScheduleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existing));

        int result = service.syncWrongQuestionsToReviewPlan(1L);
        assertEquals(0, result);
        verify(reviewScheduleMapper, never()).insert(any());
    }

    @Test
    void sync_newWrongQuestions_addedToPlan() {
        WrongQuestion wq1 = wrongQuestion(1L, 100L, 0); // 未掌握
        WrongQuestion wq2 = wrongQuestion(1L, 200L, 1); // 部分掌握
        when(wrongQuestionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(wq1, wq2));

        // 复习计划为空
        when(reviewScheduleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(reviewScheduleMapper.insert(any(QuestionReviewSchedule.class)))
                .thenReturn(1);

        int result = service.syncWrongQuestionsToReviewPlan(1L);
        assertEquals(2, result);
        verify(reviewScheduleMapper, times(2)).insert(any(QuestionReviewSchedule.class));
    }

    @Test
    void sync_partialAlreadyInPlan_addsOnlyNew() {
        WrongQuestion wq1 = wrongQuestion(1L, 100L, 0);
        WrongQuestion wq2 = wrongQuestion(1L, 200L, 1);
        when(wrongQuestionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(wq1, wq2));

        // 100 已在复习计划中
        QuestionReviewSchedule existing = new QuestionReviewSchedule();
        existing.setQuestionId(100L);
        when(reviewScheduleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existing));
        when(reviewScheduleMapper.insert(any(QuestionReviewSchedule.class)))
                .thenReturn(1);

        int result = service.syncWrongQuestionsToReviewPlan(1L);
        assertEquals(1, result);
        verify(reviewScheduleMapper, times(1)).insert(any(QuestionReviewSchedule.class));
    }

    private WrongQuestion wrongQuestion(Long userId, Long questionId, int masteryLevel) {
        WrongQuestion wq = new WrongQuestion();
        wq.setUserId(userId);
        wq.setQuestionId(questionId);
        wq.setMasteryLevel(masteryLevel);
        return wq;
    }
}
