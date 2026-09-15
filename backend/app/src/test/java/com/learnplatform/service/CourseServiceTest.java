package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.Course;
import com.learnplatform.mapper.CourseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseMapper courseMapper;

    @Test
    void learnerDetailHidesDisabledCourseButAdminCanInspectIt() {
        Course disabled = course(7L, 0);
        when(courseMapper.selectById(7L)).thenReturn(disabled);
        CourseService service = new CourseService(courseMapper);

        assertThrows(BusinessException.class, () -> service.getCourseById(7L));
        assertEquals(0, service.getAdminCourseById(7L).getStatus());
    }

    @Test
    void deleteRejectsCourseReferencedByLearningContent() {
        when(courseMapper.selectById(7L)).thenReturn(course(7L, 1));
        when(courseMapper.countReferences(7L)).thenReturn(1L);
        CourseService service = new CourseService(courseMapper);

        assertThrows(BusinessException.class, () -> service.deleteCourse(7L));

        verify(courseMapper, never()).deleteById(7L);
    }

    private Course course(Long id, Integer status) {
        Course course = new Course();
        course.setId(id);
        course.setName("课程");
        course.setStatus(status);
        return course;
    }
}
