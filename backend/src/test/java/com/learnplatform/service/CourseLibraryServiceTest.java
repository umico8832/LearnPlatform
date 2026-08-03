package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.UserCourseVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.UserCourse;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.UserCourseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseLibraryServiceTest {

    @Mock private UserCourseMapper userCourseMapper;
    @Mock private CourseMapper courseMapper;

    private CourseLibraryService service;

    @BeforeEach
    void setUp() {
        service = new CourseLibraryService(userCourseMapper, courseMapper);
    }

    @Test
    void addCourseCreatesRelationshipForEnabledCourse() {
        Course course = course(10L, 1);
        when(courseMapper.selectById(10L)).thenReturn(course);
        when(userCourseMapper.selectOne(any())).thenReturn(null);
        when(userCourseMapper.insert(any())).thenAnswer(invocation -> {
            UserCourse relation = invocation.getArgument(0);
            relation.setId(3L);
            relation.setCreateTime(LocalDateTime.of(2026, 8, 3, 10, 0));
            return 1;
        });

        UserCourseVO result = service.addCourse(7L, 10L);

        assertEquals(3L, result.getId());
        assertEquals(10L, result.getCourseId());
        assertEquals("408 数据结构", result.getCourseName());
        assertEquals("cs408-data-structures", result.getContentKey());
        ArgumentCaptor<UserCourse> captor = ArgumentCaptor.forClass(UserCourse.class);
        verify(userCourseMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(10L, captor.getValue().getCourseId());
    }

    @Test
    void addCourseIsIdempotentWhenRelationshipAlreadyExists() {
        Course course = course(10L, 1);
        UserCourse existing = relation(3L, 7L, 10L);
        when(courseMapper.selectById(10L)).thenReturn(course);
        when(userCourseMapper.selectOne(any())).thenReturn(existing);

        UserCourseVO result = service.addCourse(7L, 10L);

        assertEquals(3L, result.getId());
        verify(userCourseMapper, never()).insert(any());
    }

    @Test
    void addCourseRecoversFromConcurrentDuplicateInsert() {
        Course course = course(10L, 1);
        UserCourse concurrent = relation(4L, 7L, 10L);
        when(courseMapper.selectById(10L)).thenReturn(course);
        when(userCourseMapper.selectOne(any())).thenReturn(null, concurrent);
        when(userCourseMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        UserCourseVO result = service.addCourse(7L, 10L);

        assertEquals(4L, result.getId());
        verify(userCourseMapper, times(2)).selectOne(any());
    }

    @Test
    void addCourseRejectsDisabledCourse() {
        when(courseMapper.selectById(10L)).thenReturn(course(10L, 0));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.addCourse(7L, 10L));

        assertEquals("课程不存在或未开放", exception.getMessage());
        verifyNoInteractions(userCourseMapper);
    }

    @Test
    void getMyCoursesOnlyMapsCurrentUsersRelationships() {
        UserCourse relation = relation(3L, 7L, 10L);
        when(userCourseMapper.selectList(any())).thenReturn(List.of(relation));
        when(courseMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(course(10L, 1)));

        List<UserCourseVO> result = service.getMyCourses(7L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getCourseId());
        assertEquals("408 数据结构", result.get(0).getCourseName());
    }

    private Course course(Long id, Integer status) {
        Course course = new Course();
        course.setId(id);
        course.setName("408 数据结构");
        course.setStatus(status);
        course.setContentKey("cs408-data-structures");
        course.setContentSource("AISTU");
        return course;
    }

    private UserCourse relation(Long id, Long userId, Long courseId) {
        UserCourse relation = new UserCourse();
        relation.setId(id);
        relation.setUserId(userId);
        relation.setCourseId(courseId);
        relation.setCreateTime(LocalDateTime.of(2026, 8, 3, 10, 0));
        return relation;
    }
}
