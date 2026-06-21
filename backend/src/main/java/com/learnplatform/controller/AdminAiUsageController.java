package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.AiUsageOverviewVO;
import com.learnplatform.dto.AiUsageReportVO;
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

    @Operation(summary = "获取 AI 调用总览", description = "包含全局统计、真实 Tokens、已配置单价的成本、功能/模型分布、每日趋势、Top 活跃用户和最近失败调用")
    @GetMapping("/overview")
    public R<AiUsageOverviewVO> getOverview(
            @Parameter(description = "最近天数，默认 30") @RequestParam(required = false) Integer days) {
        return R.ok(aiUsageService.getOverview(days));
    }

    @Operation(summary = "获取 AI 运营报告", description = "比较当前与前一等长周期，并返回失败率、异常耗时和异常用量提醒")
    @GetMapping("/report")
    public R<AiUsageReportVO> getReport(
            @Parameter(description = "报告周期天数，默认 7；支持 1-90") @RequestParam(required = false) Integer days) {
        return R.ok(aiUsageService.getReport(days));
    }
}
