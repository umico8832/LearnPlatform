package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.UserCourseVO;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.dto.CourseStageAssessmentCreateRequest;
import com.learnplatform.dto.CourseStageAssessmentSubmitRequest;
import com.learnplatform.dto.CourseStageAssessmentSummaryVO;
import com.learnplatform.dto.CourseStageAssessmentVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.CourseLibraryService;
import com.learnplatform.service.CourseOverviewService;
import com.learnplatform.service.CourseStageAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.List;

@Tag(name = "个人课程库", description = "当前用户添加和查询个人课程")
@RestController
@RequestMapping("/api/my-courses")
public class CourseLibraryController {

    private final CourseLibraryService courseLibraryService;
    private final CourseOverviewService courseOverviewService;
    private final CourseStageAssessmentService stageAssessmentService;

    public CourseLibraryController(CourseLibraryService courseLibraryService,
                                   CourseOverviewService courseOverviewService,
                                   CourseStageAssessmentService stageAssessmentService) {
        this.courseLibraryService = courseLibraryService;
        this.courseOverviewService = courseOverviewService;
        this.stageAssessmentService = stageAssessmentService;
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

    @Operation(summary = "创建或恢复课程阶段测评")
    @PostMapping("/{courseId}/stage-assessments")
    public R<CourseStageAssessmentVO> startStageAssessment(
            @PathVariable Long courseId,
            @Valid @RequestBody CourseStageAssessmentCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(stageAssessmentService.start(userDetails.getUserId(), courseId, request));
    }

    @Operation(summary = "提交课程阶段测评并写回课程学习事实")
    @PostMapping("/stage-assessments/{assessmentId}/submit")
    public R<CourseStageAssessmentVO> submitStageAssessment(
            @PathVariable Long assessmentId,
            @Valid @RequestBody CourseStageAssessmentSubmitRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(stageAssessmentService.submit(assessmentId, userDetails.getUserId(), request));
    }

    @Operation(summary = "分页查询本人课程阶段测评历史")
    @GetMapping("/{courseId}/stage-assessments")
    public R<Page<CourseStageAssessmentSummaryVO>> listStageAssessments(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(stageAssessmentService.listCompleted(
                userDetails.getUserId(), courseId, pageNum, pageSize));
    }

    @Operation(summary = "查询本人已完成阶段测评复盘")
    @GetMapping("/stage-assessments/{assessmentId}")
    public R<CourseStageAssessmentVO> getStageAssessment(
            @PathVariable Long assessmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(stageAssessmentService.getCompleted(assessmentId, userDetails.getUserId()));
    }
}
