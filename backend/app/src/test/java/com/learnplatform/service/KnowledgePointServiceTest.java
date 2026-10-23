package com.learnplatform.service;

import com.learnplatform.service.content.LockedContentParents;
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
import static org.mockito.Mockito.mock;
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
        KnowledgePoint foreignParent = point(20L, 2L, 0L);
        when(knowledgePointMapper.selectById(20L)).thenReturn(foreignParent);
        ContentReferenceLockService referenceLocks = mock(ContentReferenceLockService.class);
        when(referenceLocks.lockForReference(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LockedContentParents(
                        java.util.Map.of(1L, course(1L)), java.util.Map.of(20L, foreignParent)));
        KnowledgePointService service = new KnowledgePointService(knowledgePointMapper, courseMapper, referenceLocks);

        assertThrows(BusinessException.class,
                () -> service.createKnowledgePoint(1L, 20L, "子知识点", null, 0));

        verify(knowledgePointMapper, never()).insert(org.mockito.ArgumentMatchers.any(KnowledgePoint.class));
    }

    @Test
    void deleteRejectsAnyDownstreamReference() {
        ContentReferenceLockService referenceLocks = mock(ContentReferenceLockService.class);
        org.mockito.Mockito.doThrow(new BusinessException(com.learnplatform.common.result.ResultCode.BUSINESS_ERROR,
                "知识点仍被下游内容引用，无法删除"))
                .when(referenceLocks).rejectKnowledgePointDeleteWithReferences(20L);
        KnowledgePointService service = new KnowledgePointService(knowledgePointMapper, courseMapper, referenceLocks);

        assertThrows(BusinessException.class, () -> service.deleteKnowledgePoint(20L));

        verify(knowledgePointMapper, never()).deleteById(20L);
        verify(referenceLocks).lockKnowledgePointForDelete(20L);
        verify(referenceLocks).rejectKnowledgePointDeleteWithReferences(20L);
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
