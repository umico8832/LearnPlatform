package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.query.ListQuery;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.common.time.BusinessTime;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.dto.UserCoursePageVO;
import com.learnplatform.dto.UserCourseVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.UserCourse;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.CourseLibraryQueryMapper;
import com.learnplatform.mapper.UserCourseMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CourseLibraryService {

    private final UserCourseMapper userCourseMapper;
    private final CourseMapper courseMapper;
    private final ContentReferenceLockService referenceLocks;
    private final CourseLibraryQueryMapper queryMapper;
    private final CourseOverviewBatchService overviewBatchService;

    public CourseLibraryService(UserCourseMapper userCourseMapper, CourseMapper courseMapper,
                                ContentReferenceLockService referenceLocks, CourseLibraryQueryMapper queryMapper,
                                CourseOverviewBatchService overviewBatchService) {
        this.userCourseMapper = userCourseMapper;
        this.courseMapper = courseMapper;
        this.referenceLocks = referenceLocks;
        this.queryMapper = queryMapper;
        this.overviewBatchService = overviewBatchService;
    }

    @Transactional(readOnly = true)
    public Page<UserCoursePageVO> getMyCoursesPage(Long userId, int pageNum, int pageSize, Long courseId,
                                                     String keyword, String learningStatus) {
        return getMyCoursesPage(userId, pageNum, pageSize, courseId, keyword, learningStatus, null);
    }

    @Transactional(readOnly = true)
    public Page<UserCoursePageVO> getMyCoursesPage(Long userId, int pageNum, int pageSize, Long courseId,
                                                  String keyword, String learningStatus, String sortBy) {
        String sort = sortBy == null || sortBy.isBlank() ? "ADDED_AT" : sortBy.trim();
        if (!java.util.Set.of("ADDED_AT", "LAST_LEARNING_TIME").contains(sort)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "排序方式无效");
        }
        Page<UserCourseVO> page = ListQuery.page(pageNum, pageSize);
        String normalized = ListQuery.likePattern(keyword);
        String status = learningStatus == null ? null : learningStatus.trim();
        if (status != null && !status.isEmpty()
                && !java.util.Set.of("STARTED", "NOT_STARTED", "DUE_REVIEW").contains(status)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "学习状态无效");
        }
        LocalDate today = BusinessTime.today();
        Page<UserCourseVO> source = queryMapper.selectPage(
                page, userId, courseId, normalized, status, today, sort);
        Map<Long, CourseOverviewVO> overviews = overviewBatchService.getOverviews(
                userId,
                source.getRecords().stream().map(UserCourseVO::getCourseId).toList(), today);
        Page<UserCoursePageVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(course -> {
            CourseOverviewVO overview = overviews.get(course.getCourseId());
            overview.setCourseName(course.getCourseName());
            return UserCoursePageVO.from(course, overview);
        }).toList());
        return result;
    }

    @Transactional
    public UserCourseVO addCourse(Long userId, Long courseId) {
        Course course = referenceLocks.lockForReference(Collections.singletonList(courseId), List.of())
                .courses().get(courseId);
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
                .filter(course -> Integer.valueOf(1).equals(course.getStatus()))
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
