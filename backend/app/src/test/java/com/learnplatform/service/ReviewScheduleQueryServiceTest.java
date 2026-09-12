package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.dto.ReviewStatsVO;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewScheduleQueryServiceTest {

    @Mock private QuestionReviewScheduleMapper reviewScheduleMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private ReviewScheduleCardViewService cardViewService;

    private ReviewScheduleQueryService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""), QuestionReviewSchedule.class);
        service = new ReviewScheduleQueryService(
                reviewScheduleMapper, questionMapper, knowledgePointMapper, cardViewService);
    }

    @Test
    void dueCardsAreRestrictedToRequestedCourse() {
        when(reviewScheduleMapper.selectList(any())).thenReturn(List.of(schedule(101L), schedule(202L)));
        when(questionMapper.selectList(any())).thenReturn(List.of(question(101L, 10L)));
        when(cardViewService.toViews(any(), any())).thenReturn(List.of(view(101L)));

        List<ReviewScheduleVO> cards = service.getDueReviewCards(7L, 10L, 30);

        assertEquals(List.of(101L), cards.stream().map(ReviewScheduleVO::getQuestionId).toList());
        ArgumentCaptor<LambdaQueryWrapper<QuestionReviewSchedule>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reviewScheduleMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        int courseFilterIndex = Math.max(sql.indexOf("questionId IN"), sql.indexOf("question_id IN"));
        assertTrue(courseFilterIndex >= 0 && courseFilterIndex < sql.indexOf("LIMIT"), sql);
    }

    @Test
    void dueCardsCanFocusTheServerSelectedQuestion() {
        when(reviewScheduleMapper.selectList(any())).thenReturn(List.of(schedule(101L), schedule(102L)));
        when(questionMapper.selectList(any())).thenReturn(List.of(question(101L, 10L), question(102L, 10L)));
        when(cardViewService.toViews(any(), any())).thenReturn(List.of(view(101L)));

        List<ReviewScheduleVO> cards = service.getDueReviewCards(7L, 10L, 101L, 30);

        assertEquals(List.of(101L), cards.stream().map(ReviewScheduleVO::getQuestionId).toList());
    }

    @Test
    void dueCardsCanBeFilteredByKnowledgePoint() {
        when(knowledgePointMapper.selectQuestionIdsByKnowledgePointId(31L)).thenReturn(List.of(101L));
        when(reviewScheduleMapper.selectList(any())).thenReturn(List.of(schedule(101L), schedule(202L)));
        when(questionMapper.selectList(any())).thenReturn(List.of(question(101L, 10L)));
        when(cardViewService.toViews(any(), any())).thenReturn(List.of(view(101L)));

        List<ReviewScheduleVO> cards = service.getDueReviewCards(7L, 10L, null, 31L, 30);

        assertEquals(List.of(101L), cards.stream().map(ReviewScheduleVO::getQuestionId).toList());
        verify(knowledgePointMapper).selectQuestionIdsByKnowledgePointId(31L);
    }

    @Test
    void reviewStatsKeepExistingCategoryAndStreakSemantics() {
        QuestionReviewSchedule newCard = schedule(101L);
        newCard.setTotalReviews(0);
        newCard.setIntervalDays(0);
        newCard.setEaseFactor(new BigDecimal("2.50"));

        QuestionReviewSchedule masteredDifficultCard = schedule(202L);
        masteredDifficultCard.setTotalReviews(3);
        masteredDifficultCard.setIntervalDays(21);
        masteredDifficultCard.setEaseFactor(new BigDecimal("1.80"));

        when(reviewScheduleMapper.selectCount(any()))
                .thenReturn(2L, 1L, 1L, 1L, 1L, 0L);
        when(reviewScheduleMapper.selectList(any()))
                .thenReturn(List.of(newCard, masteredDifficultCard));

        ReviewStatsVO stats = service.getReviewStats(7L);

        assertEquals(2, stats.getTotalCards());
        assertEquals(1, stats.getDueToday());
        assertEquals(1, stats.getOverdue());
        assertEquals(1, stats.getReviewedToday());
        assertEquals(1, stats.getNewCards());
        assertEquals(1, stats.getMasteredCards());
        assertEquals(1, stats.getDifficultCards());
        assertEquals(1, stats.getStreakDays());
    }

    private QuestionReviewSchedule schedule(Long questionId) {
        QuestionReviewSchedule schedule = new QuestionReviewSchedule();
        schedule.setQuestionId(questionId);
        schedule.setNextReviewDate(LocalDate.now());
        return schedule;
    }

    private Question question(Long id, Long courseId) {
        Question question = new Question();
        question.setId(id);
        question.setCourseId(courseId);
        question.setContent("测试题目 " + id);
        question.setQuestionType("SINGLE_CHOICE");
        return question;
    }

    private ReviewScheduleVO view(Long questionId) {
        ReviewScheduleVO view = new ReviewScheduleVO();
        view.setQuestionId(questionId);
        return view;
    }
}
