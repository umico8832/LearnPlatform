package com.learnplatform.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.mapper.CourseLearningFactMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseOverviewTargetServiceTest {
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private CourseLearningFactMapper factMapper;
    private CourseOverviewTargetService targetService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), KnowledgePoint.class);
        targetService = new CourseOverviewTargetService(knowledgePointMapper, factMapper);
    }

    @Test
    void usesDatabaseTutorProgressAndRetainsAggregateReasons() {
        when(factMapper.selectTutorProgress(7L, 10L)).thenReturn(List.of(progress(41L, "IN_PROGRESS")));
        when(knowledgePointMapper.selectOne(any())).thenReturn(root());
        CourseOverviewVO overview = totals(2, 1, 3, 4);
        CourseOverviewTargetService.TargetSnapshot snapshot = targetService.build(7L, 10L, overview, 21L, 22L);
        assertEquals("IN_PROGRESS", snapshot.tutorProgress().get(0).getStatus());
        assertEquals(List.of("TUTOR", "DUE_REVIEW", "WRONG_QUESTION", "COURSE_SEQUENCE"),
                snapshot.recommendedTargets().stream().map(CourseOverviewVO.LearningTargetVO::getType).toList());
        assertEquals("有 3 道题已到复习时间", snapshot.recommendedTargets().get(1).getReason());
        assertEquals("有 4 道错题仍待巩固", snapshot.recommendedTargets().get(2).getReason());
    }

    @Test
    void usesPlatformRootConventionForDefaultTarget() {
        when(factMapper.selectTutorProgress(7L, 10L)).thenReturn(List.of());
        when(knowledgePointMapper.selectOne(any())).thenReturn(root());
        CourseOverviewTargetService.TargetSnapshot snapshot = targetService.build(
                7L, 10L, totals(0, 0, 0, 0), null, null);
        assertEquals("COURSE_SEQUENCE", snapshot.recommendedTargets().get(0).getType());
        assertEquals(31L, snapshot.recommendedTargets().get(0).getKnowledgePointId());
    }

    private CourseOverviewVO totals(int answered, int correct, int due, int wrong) {
        CourseOverviewVO value = new CourseOverviewVO();
        value.setAnsweredCount(answered); value.setCorrectCount(correct);
        value.setDueReviewCount(due); value.setUnresolvedWrongCount(wrong);
        return value;
    }
    private CourseOverviewVO.TutorProgressVO progress(Long id, String status) {
        CourseOverviewVO.TutorProgressVO value = new CourseOverviewVO.TutorProgressVO();
        value.setKnowledgePointId(id); value.setTitle("课程内容"); value.setStatus(status);
        return value;
    }
    private KnowledgePoint root() {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(31L); point.setName("基本概念");
        return point;
    }
}
