package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.UserCourseVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.UserCourse;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.UserCourseMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CourseLibraryService {

    private final UserCourseMapper userCourseMapper;
    private final CourseMapper courseMapper;

    public CourseLibraryService(UserCourseMapper userCourseMapper, CourseMapper courseMapper) {
        this.userCourseMapper = userCourseMapper;
        this.courseMapper = courseMapper;
    }

    @Transactional
    public UserCourseVO addCourse(Long userId, Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null || !Integer.valueOf(1).equals(course.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在或未开放");
        }

        UserCourse existing = findRelationship(userId, courseId);
        if (existing != null) {
            return UserCourseVO.from(existing, course);
        }

        UserCourse relationship = new UserCourse();
        relationship.setUserId(userId);
        relationship.setCourseId(courseId);
        try {
            userCourseMapper.insert(relationship);
            return UserCourseVO.from(relationship, course);
        } catch (DuplicateKeyException exception) {
            UserCourse concurrentRelationship = findRelationship(userId, courseId);
            if (concurrentRelationship == null) {
                throw exception;
            }
            return UserCourseVO.from(concurrentRelationship, course);
        }
    }

    public List<UserCourseVO> getMyCourses(Long userId) {
        LambdaQueryWrapper<UserCourse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCourse::getUserId, userId)
                .orderByDesc(UserCourse::getCreateTime);
        List<UserCourse> relationships = userCourseMapper.selectList(wrapper);
        if (relationships.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> courseIds = relationships.stream()
                .map(UserCourse::getCourseId)
                .toList();
        Map<Long, Course> courses = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(
                        Course::getId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        return relationships.stream()
                .filter(relationship -> courses.containsKey(relationship.getCourseId()))
                .map(relationship -> UserCourseVO.from(
                        relationship, courses.get(relationship.getCourseId())))
                .toList();
    }

    private UserCourse findRelationship(Long userId, Long courseId) {
        LambdaQueryWrapper<UserCourse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCourse::getUserId, userId)
                .eq(UserCourse::getCourseId, courseId);
        return userCourseMapper.selectOne(wrapper);
    }
}
