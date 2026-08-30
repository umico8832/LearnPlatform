package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.LearningPlanRequest;
import com.learnplatform.dto.LearningPlanVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.LearningPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习计划控制器
 */
@Tag(name = "学习计划", description = "学习计划与每日目标管理")
@RestController
@RequestMapping("/api/learning-plan")
public class LearningPlanController {

    private final LearningPlanService learningPlanService;

    public LearningPlanController(LearningPlanService learningPlanService) {
        this.learningPlanService = learningPlanService;
    }

    @Operation(summary = "获取学习计划（含今日进度和连续打卡）")
    @GetMapping
    public R<LearningPlanVO> getPlan(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(learningPlanService.getPlan(userDetails.getUserId()));
    }

    @Operation(summary = "更新每日刷题目标")
    @PutMapping
    public R<LearningPlanVO> updateDailyGoal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody LearningPlanRequest request) {
        return R.ok(learningPlanService.updateDailyGoal(userDetails.getUserId(), request));
    }
}
