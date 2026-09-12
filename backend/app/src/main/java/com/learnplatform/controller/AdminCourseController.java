package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.CourseVO;
import com.learnplatform.service.CourseService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端课程控制器
 */
@RestController
@RequestMapping("/api/admin/courses")
public class AdminCourseController {

    private final CourseService courseService;

    public AdminCourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    public R<CourseVO> createCourse(@RequestBody CreateCourseRequest request) {
        return R.ok(courseService.createCourse(request.getName(), request.getDescription(), request.getSortOrder()));
    }

    @PutMapping("/{id}")
    public R<CourseVO> updateCourse(@PathVariable Long id, @RequestBody CreateCourseRequest request) {
        return R.ok(courseService.updateCourse(id, request.getName(),
                request.getDescription(), request.getSortOrder()));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return R.ok();
    }

    /**
     * 创建/更新课程请求体
     */
    public static class CreateCourseRequest {
        private String name;
        private String description;
        private Integer sortOrder;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}
