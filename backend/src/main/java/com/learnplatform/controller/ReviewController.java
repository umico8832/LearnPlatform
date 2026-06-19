package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.*;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.SpacedRepetitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 间隔重复复习控制器
 */
@Tag(name = "间隔重复复习", description = "基于 SM-2 算法的智能复习计划")
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final SpacedRepetitionService spacedRepetitionService;

    public ReviewController(SpacedRepetitionService spacedRepetitionService) {
        this.spacedRepetitionService = spacedRepetitionService;
    }

    @Operation(summary = "获取复习统计概览")
    @GetMapping("/stats")
    public R<ReviewStatsVO> getStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(spacedRepetitionService.getReviewStats(userDetails.getUserId()));
    }

    @Operation(summary = "获取今日待复习题目")
    @GetMapping("/due")
    public R<List<ReviewScheduleVO>> getDueCards(
            @Parameter(description = "课程ID筛选") @RequestParam(required = false) Long courseId,
            @Parameter(description = "数量限制") @RequestParam(required = false, defaultValue = "20") Integer limit,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(spacedRepetitionService.getDueReviewCards(userDetails.getUserId(), courseId, limit));
    }

    @Operation(summary = "获取所有复习计划卡片")
    @GetMapping("/cards")
    public R<List<ReviewScheduleVO>> getAllCards(
            @Parameter(description = "课程ID筛选") @RequestParam(required = false) Long courseId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(spacedRepetitionService.getAllReviewCards(userDetails.getUserId(), courseId));
    }

    @Operation(summary = "将题目加入复习计划")
    @PostMapping("/add/{questionId}")
    public R<Void> addToPlan(
            @PathVariable Long questionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        spacedRepetitionService.addToReviewPlan(userDetails.getUserId(), questionId);
        return R.ok(null);
    }

    @Operation(summary = "提交复习答案并更新调度")
    @PostMapping("/submit")
    public R<ReviewScheduleVO> submitReview(
            @RequestBody ReviewSubmitRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ReviewScheduleVO vo = spacedRepetitionService.submitReview(request, userDetails.getUserId());
        return R.ok(vo);
    }

    @Operation(summary = "将题目移出复习计划")
    @DeleteMapping("/remove/{questionId}")
    public R<Void> removeFromPlan(
            @PathVariable Long questionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        spacedRepetitionService.removeFromReviewPlan(userDetails.getUserId(), questionId);
        return R.ok(null);
    }

    @Operation(summary = "重置题目复习进度")
    @PostMapping("/reset/{questionId}")
    public R<Void> resetProgress(
            @PathVariable Long questionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        spacedRepetitionService.resetReviewProgress(userDetails.getUserId(), questionId);
        return R.ok(null);
    }
}