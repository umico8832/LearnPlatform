package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.CourseVO;
import com.learnplatform.service.CourseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程控制器（用户端）
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * 获取课程列表（分页）
     */
    @GetMapping
    public R<Page<CourseVO>> getCoursePage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return R.ok(courseService.getCoursePage(pageNum, pageSize, keyword));
    }

    /**
     * 获取所有启用的课程（不分页）
     */
    @GetMapping("/list")
    public R<List<CourseVO>> getAllCourses() {
        return R.ok(courseService.getAllEnabledCourses());
    }

    /**
     * 获取课程详情
     */
    @GetMapping("/{id}")
    public R<CourseVO> getCourseById(@PathVariable Long id) {
        return R.ok(courseService.getCourseById(id));
    }
}
