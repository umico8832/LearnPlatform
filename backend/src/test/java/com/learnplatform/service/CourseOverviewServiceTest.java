package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.CourseLearningEvent;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseLearningEventMapper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import com.learnplatform.mapper.UserCourseMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseOverviewServiceTest {

    @Mock private UserCourseMapper userCourseMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private CourseLearningEventMapper eventMapper;
    @Mock private WrongQuestionMapper wrongQuestionMapper;
    @Mock private QuestionReviewScheduleMapper reviewScheduleMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;

    private CourseOverviewService service;

    @BeforeEach
    void setUp() {
        service = new CourseOverviewService(userCourseMapper, courseMapper, eventMapper,
                wrongQuestionMapper, reviewScheduleMapper, questionMapper, knowledgePointMapper);
    }

    @Test
    void aggregatesOnlyCurrentCoursesEvidenceAndPrioritizesDueReview() {
        when(userCourseMapper.selectCount(any())).thenReturn(1L);
        when(courseMapper.selectById(10L)).thenReturn(course());
        when(eventMapper.selectList(any())).thenReturn(List.of(event(true), event(false)));
        when(questionMapper.selectList(any())).thenReturn(List.of(question(21L), question(22L)));
        when(wrongQuestionMapper.selectList(any())).thenReturn(List.of(wrongQuestion(22L, 3)));
        when(reviewScheduleMapper.selectList(any())).thenReturn(List.of(reviewSchedule(21L)));
        when(knowledgePointMapper.selectOne(any())).thenReturn(rootKnowledgePoint());

        CourseOverviewVO overview = service.getOverview(7L, 10L);

        assertEquals(2, overview.getAnsweredCount());
        assertEquals(1, overview.getCorrectCount());
        assertEquals(1, overview.getDueReviewCount());
        assertEquals(1, overview.getUnresolvedWrongCount());
        assertEquals("DUE_REVIEW", overview.getRecommendedTargets().get(0).getType());
        assertEquals(21L, overview.getRecommendedTargets().get(0).getQuestionId());
        assertEquals("COURSE_SEQUENCE", overview.getRecommendedTargets().get(2).getType());
    }

    @Test
    void rejectsOverviewForCourseOutsideUsersLibrary() {
        when(userCourseMapper.selectCount(any())).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getOverview(7L, 10L));

        assertEquals("请先将课程加入个人课程库", exception.getMessage());
    }

    private Course course() {
        Course course = new Course();
        course.setId(10L);
        course.setName("408 数据结构");
        return course;
    }

    private CourseLearningEvent event(boolean correct) {
        CourseLearningEvent event = new CourseLearningEvent();
        event.setPayloadJson("{\"isCorrect\":" + correct + "}");
        event.setOccurredTime(LocalDateTime.of(2026, 8, 3, 12, 0));
        return event;
    }

    private Question question(Long id) {
        Question question = new Question();
        question.setId(id);
        question.setCourseId(10L);
        return question;
    }

    private WrongQuestion wrongQuestion(Long questionId, int wrongCount) {
        WrongQuestion item = new WrongQuestion();
        item.setQuestionId(questionId);
        item.setWrongCount(wrongCount);
        item.setMasteryLevel(0);
        return item;
    }

    private QuestionReviewSchedule reviewSchedule(Long questionId) {
        QuestionReviewSchedule item = new QuestionReviewSchedule();
        item.setQuestionId(questionId);
        item.setNextReviewDate(LocalDate.now());
        return item;
    }

    private KnowledgePoint rootKnowledgePoint() {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(31L);
        point.setName("基本概念");
        return point;
    }
}
