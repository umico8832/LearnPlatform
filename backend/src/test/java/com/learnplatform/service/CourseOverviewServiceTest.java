package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.CourseLearningEvent;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.entity.TutorContent;
import com.learnplatform.entity.TutorSession;
import com.learnplatform.mapper.CourseLearningEventMapper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import com.learnplatform.mapper.UserCourseMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import com.learnplatform.mapper.TutorContentMapper;
import com.learnplatform.mapper.TutorSessionMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    @Mock private TutorContentMapper tutorContentMapper;
    @Mock private TutorSessionMapper tutorSessionMapper;

    private CourseOverviewService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), KnowledgePoint.class);
        service = new CourseOverviewService(userCourseMapper, courseMapper, eventMapper,
                wrongQuestionMapper, reviewScheduleMapper, questionMapper, knowledgePointMapper,
                tutorContentMapper, tutorSessionMapper);
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
        when(knowledgePointMapper.selectList(any())).thenReturn(List.of(tutorKnowledgePoint(41L)));
        when(tutorContentMapper.selectList(any())).thenReturn(List.of(tutorContent(41L, 81L)));
        when(tutorSessionMapper.selectList(any())).thenReturn(List.of());

        CourseOverviewVO overview = service.getOverview(7L, 10L);

        assertEquals(2, overview.getAnsweredCount());
        assertEquals(1, overview.getCorrectCount());
        assertEquals(1, overview.getDueReviewCount());
        assertEquals(1, overview.getUnresolvedWrongCount());
        assertEquals("TUTOR", overview.getRecommendedTargets().get(0).getType());
        assertEquals(41L, overview.getRecommendedTargets().get(0).getKnowledgePointId());
        assertEquals("DUE_REVIEW", overview.getRecommendedTargets().get(1).getType());
        assertEquals(21L, overview.getRecommendedTargets().get(1).getQuestionId());
        assertEquals("COURSE_SEQUENCE", overview.getRecommendedTargets().get(3).getType());
    }

    @Test
    void rejectsOverviewForCourseOutsideUsersLibrary() {
        when(userCourseMapper.selectCount(any())).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getOverview(7L, 10L));

        assertEquals("请先将课程加入个人课程库", exception.getMessage());
    }

    @Test
    void usesPlatformRootConventionWhenSelectingDefaultCourseTarget() {
        when(userCourseMapper.selectCount(any())).thenReturn(1L);
        when(courseMapper.selectById(10L)).thenReturn(course());
        when(eventMapper.selectList(any())).thenReturn(List.of());
        when(questionMapper.selectList(any())).thenReturn(List.of());
        when(knowledgePointMapper.selectOne(any())).thenReturn(rootKnowledgePoint());

        service.getOverview(7L, 10L);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgePoint>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(knowledgePointMapper).selectOne(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("parentId ="),
                captor.getValue().getSqlSegment());
    }

    @Test
    void excludesTutorContentAfterItsFirstCorrectCheck() {
        when(userCourseMapper.selectCount(any())).thenReturn(1L);
        when(courseMapper.selectById(10L)).thenReturn(course());
        when(eventMapper.selectList(any())).thenReturn(List.of());
        when(questionMapper.selectList(any())).thenReturn(List.of());
        when(knowledgePointMapper.selectOne(any())).thenReturn(rootKnowledgePoint());
        when(knowledgePointMapper.selectList(any())).thenReturn(List.of(tutorKnowledgePoint(41L)));
        when(tutorContentMapper.selectList(any())).thenReturn(List.of(tutorContent(41L, 81L)));
        when(tutorSessionMapper.selectList(any())).thenReturn(List.of(completedTutorSession(81L)));

        CourseOverviewVO overview = service.getOverview(7L, 10L);

        assertEquals("COURSE_SEQUENCE", overview.getRecommendedTargets().get(0).getType());
    }

    @Test
    void derivesTutorProgressFromServerSideCheckFacts() {
        when(userCourseMapper.selectCount(any())).thenReturn(1L);
        when(courseMapper.selectById(10L)).thenReturn(course());
        when(eventMapper.selectList(any())).thenReturn(List.of());
        when(questionMapper.selectList(any())).thenReturn(List.of());
        when(knowledgePointMapper.selectOne(any())).thenReturn(rootKnowledgePoint());
        KnowledgePoint first = tutorKnowledgePoint(41L);
        first.setName("ArrayStack 的按位插入");
        first.setSortOrder(20);
        KnowledgePoint second = tutorKnowledgePoint(42L);
        second.setName("ArrayStack 的容量调整");
        second.setSortOrder(30);
        KnowledgePoint third = tutorKnowledgePoint(43L);
        third.setName("ArrayStack 的按位删除");
        third.setSortOrder(40);
        when(knowledgePointMapper.selectList(any())).thenReturn(List.of(third, second, first));
        when(tutorContentMapper.selectList(any())).thenReturn(List.of(
                tutorContent(42L, 82L), tutorContent(43L, 83L), tutorContent(41L, 81L)));
        TutorSession attempted = new TutorSession();
        attempted.setTutorContentId(82L);
        attempted.setCheckCorrect(false);
        when(tutorSessionMapper.selectList(any())).thenReturn(List.of(completedTutorSession(81L), attempted));

        CourseOverviewVO overview = service.getOverview(7L, 10L);

        assertEquals(3, overview.getTutorProgress().size());
        assertEquals("COMPLETED", overview.getTutorProgress().get(0).getStatus());
        assertEquals("IN_PROGRESS", overview.getTutorProgress().get(1).getStatus());
        assertEquals("NOT_STARTED", overview.getTutorProgress().get(2).getStatus());
        assertEquals(41L, overview.getTutorProgress().get(0).getKnowledgePointId());
        assertEquals(42L, overview.getTutorProgress().get(1).getKnowledgePointId());
        assertEquals(43L, overview.getTutorProgress().get(2).getKnowledgePointId());
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

    private KnowledgePoint tutorKnowledgePoint(Long id) {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(id);
        point.setCourseId(10L);
        return point;
    }

    private TutorContent tutorContent(Long knowledgePointId, Long id) {
        TutorContent content = new TutorContent();
        content.setId(id);
        content.setKnowledgePointId(knowledgePointId);
        content.setTitle("ArrayStack 的按位插入");
        return content;
    }

    private TutorSession completedTutorSession(Long tutorContentId) {
        TutorSession session = new TutorSession();
        session.setTutorContentId(tutorContentId);
        session.setCheckCorrect(true);
        return session;
    }
}
