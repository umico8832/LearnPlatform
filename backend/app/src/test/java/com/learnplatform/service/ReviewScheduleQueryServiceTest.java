package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.State;
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
import static org.mockito.Mockito.atLeast;
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
    void dueCardsExcludeUnavailableQuestionsBeforeTheLimit() {
        when(reviewScheduleMapper.selectList(any())).thenReturn(List.of(schedule(101L)));
        when(cardViewService.toViews(any(), any())).thenReturn(List.of(view(101L)));

        service.getDueReviewCards(7L, null, 30);

        ArgumentCaptor<LambdaQueryWrapper<QuestionReviewSchedule>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reviewScheduleMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        int candidateFilter = sql.indexOf("SELECT question.id FROM question");
        int limit = sql.indexOf("LIMIT");
        assertTrue(candidateFilter >= 0 && candidateFilter < limit, sql);
    }

    @Test
    void statsCountOnlyAutomaticallyReviewableDueCardsWhileKeepingHistoricalTotals() {
        when(reviewScheduleMapper.selectCount(any())).thenReturn(2L, 1L, 1L, 0L, 0L);
        when(reviewScheduleMapper.selectList(any())).thenReturn(List.of());

        ReviewStatsVO stats = service.getReviewStats(7L);

        assertEquals(2, stats.getTotalCards(), "历史卡总数不应因缺少答案依据而丢失");
        ArgumentCaptor<LambdaQueryWrapper<QuestionReviewSchedule>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reviewScheduleMapper, atLeast(4)).selectCount(captor.capture());
        long filteredDueCounts = captor.getAllValues().stream()
                .map(LambdaQueryWrapper::getSqlSegment)
                .filter(sql -> sql.contains("SELECT question.id FROM question"))
                .count();
        assertEquals(2, filteredDueCounts, "今日待复习和逾期统计均必须排除无法自动判分的历史卡");
    }

    @Test
    void aiReviewContextExcludesUnavailableDifficultAndOverdueCards() {
        when(reviewScheduleMapper.selectCount(any())).thenReturn(0L);
        when(reviewScheduleMapper.selectList(any())).thenReturn(List.of());
        when(cardViewService.toViews(any(), any())).thenReturn(List.of());

        service.buildReviewContext(7L);

        ArgumentCaptor<LambdaQueryWrapper<QuestionReviewSchedule>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reviewScheduleMapper, atLeast(2)).selectList(captor.capture());
        long filteredSuggestions = captor.getAllValues().stream()
                .map(LambdaQueryWrapper::getSqlSegment)
                .filter(sql -> sql.contains("SELECT question.id FROM question"))
                .count();
        assertEquals(2, filteredSuggestions,
                "困难与逾期建议均不应向 AI 传递无法提交的历史卡");
    }

    @Test
    void categoriesUseOnlyFsrsStateAndLastRating() {
        QuestionReviewSchedule legacy = schedule(101L);
        legacy.setTotalReviews(15);
        legacy.setIntervalDays(100);
        legacy.setEaseFactor(new BigDecimal("1.20"));
        legacy.setLastQuality(1);
        QuestionReviewSchedule fresh = schedule(102L);
        fresh.setTotalReviews(0);
        QuestionReviewSchedule learning = fsrsCard(103L, State.LEARNING, "GOOD");
        QuestionReviewSchedule reviewAgain = fsrsCard(104L, State.REVIEW, "AGAIN");
        reviewAgain.setIntervalDays(1);
        QuestionReviewSchedule reviewHard = fsrsCard(105L, State.REVIEW, "HARD");
        QuestionReviewSchedule relearning = fsrsCard(106L, State.RELEARNING, "AGAIN");
        when(reviewScheduleMapper.selectCount(any())).thenReturn(6L, 1L, 1L, 1L);
        when(reviewScheduleMapper.selectList(any()))
                .thenReturn(List.of(legacy, fresh, learning, reviewAgain, reviewHard, relearning));

        ReviewStatsVO stats = service.getReviewStats(7L);

        assertEquals(6, stats.getTotalCards());
        assertEquals(1, stats.getDueToday());
        assertEquals(1, stats.getOverdue());
        assertEquals(1, stats.getReviewedToday());
        assertEquals(2, stats.getPendingRatingCards());
        assertEquals(1, stats.getLearningCards());
        assertEquals(2, stats.getReviewCards());
        assertEquals(1, stats.getRelearningCards());
        assertEquals(2, stats.getLastAgainCards());
        assertEquals(1, stats.getLastHardCards());
    }

    private QuestionReviewSchedule fsrsCard(long id, State state, String rating) {
        QuestionReviewSchedule schedule = schedule(id);
        Card card = Card.builder().build();
        card.setState(state);
        schedule.setFsrsCardJson(card.toJson());
        schedule.setFsrsLastRating(rating);
        return schedule;
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
