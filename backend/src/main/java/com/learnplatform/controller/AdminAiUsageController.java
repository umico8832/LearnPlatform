package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.AiUsageOverviewVO;
import com.learnplatform.service.AiUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 AI 调用分析控制器
 */
@Tag(name = "管理端-AI调用分析")
@RestController
@RequestMapping("/api/admin/ai-usage")
public class AdminAiUsageController {

    private final AiUsageService aiUsageService;

    public AdminAiUsageController(AiUsageService aiUsageService) {
        this.aiUsageService = aiUsageService;
    }

    @Operation(summary = "获取 AI 调用总览", description = "包含全局统计、功能分布、模型分布、每日趋势、Top 活跃用户、最近失败调用")
    @GetMapping("/overview")
    public R<AiUsageOverviewVO> getOverview(
            @Parameter(description = "最近天数，默认 30") @RequestParam(required = false) Integer days) {
        return R.ok(aiUsageService.getOverview(days));
    }
}