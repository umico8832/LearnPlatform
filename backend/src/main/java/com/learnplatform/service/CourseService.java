package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.CourseVO;
import com.learnplatform.entity.Course;
import com.learnplatform.mapper.CourseMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseService {

    private final CourseMapper courseMapper;

    public CourseService(CourseMapper courseMapper) {
        this.courseMapper = courseMapper;
    }

    /**
     * 获取课程列表（分页）
     */
    public Page<CourseVO> getCoursePage(int pageNum, int pageSize, String keyword) {
        Page<Course> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Course::getName, keyword);
        }
        wrapper.orderByAsc(Course::getSortOrder).orderByDesc(Course::getCreateTime);
        Page<Course> result = courseMapper.selectPage(page, wrapper);
        
        Page<CourseVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(CourseVO::fromEntity)
                .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 获取所有启用的课程
     */
    public List<CourseVO> getAllEnabledCourses() {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Course::getStatus, 1)
               .orderByAsc(Course::getSortOrder)
               .orderByDesc(Course::getCreateTime);
        return courseMapper.selectList(wrapper).stream()
                .map(CourseVO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取课程详情
     */
    public CourseVO getCourseById(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }
        return CourseVO.fromEntity(course);
    }

    /**
     * 创建课程
     */
    public CourseVO createCourse(String name, String description, Integer sortOrder) {
        Course course = new Course();
        course.setName(name);
        course.setDescription(description);
        course.setSortOrder(sortOrder != null ? sortOrder : 0);
        course.setStatus(1);
        course.setDeleted(0);
        courseMapper.insert(course);
        return CourseVO.fromEntity(course);
    }

    /**
     * 更新课程
     */
    public CourseVO updateCourse(Long id, String name, String description, Integer sortOrder) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }
        if (name != null) course.setName(name);
        if (description != null) course.setDescription(description);
        if (sortOrder != null) course.setSortOrder(sortOrder);
        courseMapper.updateById(course);
        return CourseVO.fromEntity(course);
    }

    /**
     * 删除课程
     */
    public void deleteCourse(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }
        courseMapper.deleteById(id);
    }
}