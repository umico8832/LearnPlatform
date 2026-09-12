package com.learnplatform.service;

import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewScheduleCardViewServiceTest {

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private CourseMapper courseMapper;

    @Test
    void mapsOverdueDifficultCardWithSanitizedQuestionAndCourse() {
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

        ReviewScheduleCardViewService service = new ReviewScheduleCardViewService(questionMapper, courseMapper);
        ReviewScheduleVO view = service.toViews(List.of(schedule), LocalDate.of(2026, 9, 5)).getFirst();

        assertEquals("困难", view.getStatusLabel());
        assertEquals("二叉树 遍历", view.getQuestionContent());
        assertEquals("数据结构", view.getCourseName());
        assertEquals(3, view.getOverdueDays());
        assertFalse(view.getQuestionContent().contains("<"));
    }
}
