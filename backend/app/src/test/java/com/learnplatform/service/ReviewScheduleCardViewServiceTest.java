package com.learnplatform.service;

import com.learnplatform.common.time.BusinessTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.service.understanding.QuestionUnderstandingContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewScheduleCardViewServiceTest {

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private QuestionUnderstandingContent understanding;

    @Test
    void mapsLegacyCardWithoutInferringStateFromEaseFactor() {
        QuestionReviewSchedule schedule = new QuestionReviewSchedule();
        schedule.setId(1L);
        schedule.setQuestionId(10L);
        schedule.setTotalReviews(2);
        schedule.setEaseFactor(new BigDecimal("1.80"));
        schedule.setIntervalDays(7);
        schedule.setNextReviewDate(LocalDate.of(2026, 9, 2));

        Question question = new Question();
        question.setId(10L);
        question.setCourseId(20L);
        question.setContent("<p>二叉树\n遍历</p>");
        question.setQuestionType("SINGLE_CHOICE");
        question.setDifficulty(3);
        Course course = new Course();
        course.setId(20L);
        course.setName("数据结构");
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(question));
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of(course));

        var service = new ReviewScheduleCardViewService(questionMapper, courseMapper, understanding);
        ReviewScheduleVO view = service.toViews(7L, List.of(schedule), LocalDate.of(2026, 9, 5)).getFirst();

        assertEquals("待首次 FSRS 评分", view.getStatusLabel());
        assertEquals("UNRATED", view.getSchedulingState());
        JsonNode data = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .findAndRegisterModules().valueToTree(view);
        assertTrue(data.has("hasUnderstanding"));
        assertFalse(data.get("hasUnderstanding").asBoolean());
        assertTrue(data.has("lastRating"));
        assertTrue(data.get("lastRating").isNull());
        assertEquals("二叉树 遍历", view.getQuestionContent());
        assertEquals("数据结构", view.getCourseName());
        assertEquals(3, view.getOverdueDays());
        assertFalse(view.getQuestionContent().contains("<"));
    }

    @Test
    void retainsLegacyCardsButMarksOnlyAutomaticallyGradableCardsAvailable() {
        QuestionReviewSchedule availableSchedule = new QuestionReviewSchedule();
        availableSchedule.setId(1L);
        availableSchedule.setQuestionId(10L);
        QuestionReviewSchedule legacySchedule = new QuestionReviewSchedule();
        legacySchedule.setId(2L);
        legacySchedule.setQuestionId(20L);

        Question availableQuestion = question(10L, 30L, "可自动判分题");
        Question legacyQuestion = question(20L, 30L, "缺少判分依据的历史题");
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(availableQuestion, legacyQuestion));
        when(questionMapper.selectList(any())).thenReturn(List.of(availableQuestion));
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of());

        when(understanding.available(eq(7L), anyList())).thenReturn(Set.of(10L));

        var service = new ReviewScheduleCardViewService(questionMapper, courseMapper, understanding);
        List<ReviewScheduleVO> views = service.toViews(7L,
                List.of(availableSchedule, legacySchedule), LocalDate.of(2026, 10, 2));

        verify(understanding).available(eq(7L), anyList());
        assertTrue(views.get(0).getHasUnderstanding());
        assertFalse(views.get(1).getHasUnderstanding());
        assertEquals(2, views.size(), "历史卡仍应显示，供用户查看或移出计划");
        assertTrue(views.get(0).isAvailableForReview());
        assertFalse(views.get(1).isAvailableForReview());
    }

    @Test
    void emptyCardsDoNotReadUnderstandingContent() {
        var service = new ReviewScheduleCardViewService(questionMapper, courseMapper, understanding);
        assertTrue(service.toViews(7L, List.of(), BusinessTime.today()).isEmpty());
        verifyNoInteractions(questionMapper, courseMapper, understanding);
    }

    @Test
    void singleCardUsesScheduleOwnerForUnderstandingGate() {
        QuestionReviewSchedule schedule = new QuestionReviewSchedule();
        schedule.setUserId(8L);
        schedule.setQuestionId(10L);
        Question question = question(10L, null, "单题");
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(question));
        when(understanding.available(8L, List.of(question))).thenReturn(Set.of(10L));
        var service = new ReviewScheduleCardViewService(questionMapper, courseMapper, understanding);
        assertTrue(service.toView(schedule).getHasUnderstanding());
        verify(understanding).available(8L, List.of(question));
    }

    private Question question(Long id, Long courseId, String content) {
        Question question = new Question();
        question.setId(id);
        question.setCourseId(courseId);
        question.setContent(content);
        question.setQuestionType("SINGLE_CHOICE");
        return question;
    }
}
