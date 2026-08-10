package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.mapper.CourseMapper;
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

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpacedRepetitionQueryTest {

    @Mock private QuestionReviewScheduleMapper reviewScheduleMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private CourseMapper courseMapper;

    private SpacedRepetitionService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""), QuestionReviewSchedule.class);
        service = new SpacedRepetitionService(reviewScheduleMapper, questionMapper, courseMapper,
                null, null, null, null, null);
    }

    @Test
    void dueCardsAreRestrictedToRequestedCourse() {
        when(reviewScheduleMapper.selectList(any())).thenReturn(List.of(schedule(101L), schedule(202L)));
        when(questionMapper.selectList(any())).thenReturn(List.of(question(101L, 10L)));
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(question(101L, 10L), question(202L, 20L)));
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of(course(10L), course(20L)));

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
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(question(101L, 10L)));
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of(course(10L)));

        List<ReviewScheduleVO> cards = service.getDueReviewCards(7L, 10L, 101L, 30);

        assertEquals(List.of(101L), cards.stream().map(ReviewScheduleVO::getQuestionId).toList());
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

    private Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        course.setName("课程 " + id);
        return course;
    }
}
