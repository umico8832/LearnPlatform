package com.learnplatform.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.entity.TutorContent;
import com.learnplatform.entity.TutorSession;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.TutorContentMapper;
import com.learnplatform.mapper.TutorSessionMapper;
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
class CourseOverviewTargetServiceTest {

    @Mock
    private KnowledgePointMapper knowledgePointMapper;

    @Mock
    private TutorContentMapper tutorContentMapper;

    @Mock
    private TutorSessionMapper tutorSessionMapper;

    private CourseOverviewTargetService targetService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), KnowledgePoint.class);
        targetService = new CourseOverviewTargetService(
                knowledgePointMapper, tutorContentMapper, tutorSessionMapper);
    }

    @Test
    void derivesTutorProgressAndBuildsUnifiedTargetOrder() {
        KnowledgePoint first = knowledgePoint(41L, 20);
        KnowledgePoint second = knowledgePoint(42L, 30);
        KnowledgePoint third = knowledgePoint(43L, 40);
        when(knowledgePointMapper.selectList(any())).thenReturn(List.of(third, second, first));
        when(tutorContentMapper.selectList(any())).thenReturn(List.of(
                tutorContent(42L, 82L), tutorContent(43L, 83L), tutorContent(41L, 81L)));
        TutorSession completed = tutorSession(81L, true);
        TutorSession attempted = tutorSession(82L, false);
        when(tutorSessionMapper.selectList(any())).thenReturn(List.of(completed, attempted));
        when(knowledgePointMapper.selectOne(any())).thenReturn(rootKnowledgePoint());

        CourseOverviewTargetService.TargetSnapshot snapshot = targetService.build(
                7L, 10L, List.of(reviewSchedule(21L)), List.of(wrongQuestion(22L)));

        assertEquals(List.of("COMPLETED", "IN_PROGRESS", "NOT_STARTED"),
                snapshot.tutorProgress().stream().map(item -> item.getStatus()).toList());
        assertEquals(List.of(41L, 42L, 43L),
                snapshot.tutorProgress().stream().map(item -> item.getKnowledgePointId()).toList());
        assertEquals(List.of("TUTOR", "DUE_REVIEW", "WRONG_QUESTION", "COURSE_SEQUENCE"),
                snapshot.recommendedTargets().stream().map(item -> item.getType()).toList());
        assertEquals(42L, snapshot.recommendedTargets().get(0).getKnowledgePointId());
        assertEquals(21L, snapshot.recommendedTargets().get(1).getQuestionId());
        assertEquals(22L, snapshot.recommendedTargets().get(2).getQuestionId());
    }

    @Test
    void usesPlatformRootConventionForDefaultTarget() {
        when(knowledgePointMapper.selectList(any())).thenReturn(List.of());
        when(knowledgePointMapper.selectOne(any())).thenReturn(rootKnowledgePoint());

        CourseOverviewTargetService.TargetSnapshot snapshot =
                targetService.build(7L, 10L, List.of(), List.of());

        assertEquals("COURSE_SEQUENCE", snapshot.recommendedTargets().get(0).getType());
        assertEquals(31L, snapshot.recommendedTargets().get(0).getKnowledgePointId());
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgePoint>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(knowledgePointMapper).selectOne(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("parentId ="),
                captor.getValue().getSqlSegment());
    }

    private KnowledgePoint knowledgePoint(Long id, int sortOrder) {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(id);
        point.setCourseId(10L);
        point.setSortOrder(sortOrder);
        return point;
    }

    private KnowledgePoint rootKnowledgePoint() {
        KnowledgePoint point = knowledgePoint(31L, 1);
        point.setName("基本概念");
        return point;
    }

    private TutorContent tutorContent(Long knowledgePointId, Long id) {
        TutorContent content = new TutorContent();
        content.setId(id);
        content.setKnowledgePointId(knowledgePointId);
        content.setTitle("课程内容 " + id);
        return content;
    }

    private TutorSession tutorSession(Long contentId, boolean correct) {
        TutorSession session = new TutorSession();
        session.setTutorContentId(contentId);
        session.setCheckCorrect(correct);
        return session;
    }

    private QuestionReviewSchedule reviewSchedule(Long questionId) {
        QuestionReviewSchedule schedule = new QuestionReviewSchedule();
        schedule.setQuestionId(questionId);
        schedule.setNextReviewDate(LocalDate.now());
        return schedule;
    }

    private WrongQuestion wrongQuestion(Long questionId) {
        WrongQuestion question = new WrongQuestion();
        question.setQuestionId(questionId);
        question.setWrongCount(1);
        return question;
    }
}
