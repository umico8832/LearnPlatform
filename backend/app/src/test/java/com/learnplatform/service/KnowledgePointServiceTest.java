package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgePointServiceTest {

    @Mock
    private KnowledgePointMapper knowledgePointMapper;
    @Mock
    private CourseMapper courseMapper;

    @Test
    void createRejectsParentFromAnotherCourse() {
        when(courseMapper.selectById(1L)).thenReturn(course(1L));
        when(knowledgePointMapper.selectById(20L)).thenReturn(point(20L, 2L, 0L));
        KnowledgePointService service = new KnowledgePointService(knowledgePointMapper, courseMapper);

        assertThrows(BusinessException.class,
                () -> service.createKnowledgePoint(1L, 20L, "子知识点", null, 0));

        verify(knowledgePointMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteRejectsAnyDownstreamReference() {
        when(knowledgePointMapper.selectById(20L)).thenReturn(point(20L, 1L, 0L));
        when(knowledgePointMapper.countReferences(20L)).thenReturn(1L);
        KnowledgePointService service = new KnowledgePointService(knowledgePointMapper, courseMapper);

        assertThrows(BusinessException.class, () -> service.deleteKnowledgePoint(20L));

        verify(knowledgePointMapper, never()).deleteById(20L);
    }

    private Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }

    private KnowledgePoint point(Long id, Long courseId, Long parentId) {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(id);
        point.setCourseId(courseId);
        point.setParentId(parentId);
        return point;
    }
}
