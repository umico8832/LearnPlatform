package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.UserCourseVO;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.CourseLibraryService;
import com.learnplatform.service.CourseOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "个人课程库", description = "当前用户添加和查询个人课程")
@RestController
@RequestMapping("/api/my-courses")
public class CourseLibraryController {

    private final CourseLibraryService courseLibraryService;
    private final CourseOverviewService courseOverviewService;

    public CourseLibraryController(CourseLibraryService courseLibraryService,
                                   CourseOverviewService courseOverviewService) {
        this.courseLibraryService = courseLibraryService;
        this.courseOverviewService = courseOverviewService;
    }

    @Operation(summary = "添加课程到个人课程库")
    @PostMapping("/{courseId}")
    public R<UserCourseVO> addCourse(
            @PathVariable Long courseId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(courseLibraryService.addCourse(userDetails.getUserId(), courseId));
    }

    @Operation(summary = "查询个人课程库")
    @GetMapping
    public R<List<UserCourseVO>> getMyCourses(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(courseLibraryService.getMyCourses(userDetails.getUserId()));
    }

    @Operation(summary = "查询课程学习总览")
    @GetMapping("/{courseId}/overview")
    public R<CourseOverviewVO> getCourseOverview(
            @PathVariable Long courseId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(courseOverviewService.getOverview(userDetails.getUserId(), courseId));
    }

    @Operation(summary = "按统一课程状态选择下一学习目标")
    @PostMapping("/{courseId}/start-learning")
    public R<CourseOverviewVO.LearningTargetVO> startLearning(
            @PathVariable Long courseId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(courseOverviewService.selectStartTarget(userDetails.getUserId(), courseId));
    }
}
