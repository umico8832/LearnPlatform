package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.AiUsageAlertVO;
import com.learnplatform.dto.AiUsageOverviewVO;
import com.learnplatform.dto.AiUsageReportVO;
import com.learnplatform.dto.AiLearningEffectVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiUsageReportService;
import com.learnplatform.service.AiUsageService;
import com.learnplatform.service.AiLearningEffectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端 AI 调用分析控制器
 */
@Tag(name = "管理端-AI调用分析")
@RestController
@RequestMapping("/api/admin/ai-usage")
public class AdminAiUsageController {

    private final AiUsageService aiUsageService;
    private final AiUsageReportService aiUsageReportService;
    private final AiLearningEffectService learningEffectService;

    public AdminAiUsageController(AiUsageService aiUsageService,
                                  AiUsageReportService aiUsageReportService,
                                  AiLearningEffectService learningEffectService) {
        this.aiUsageService = aiUsageService;
        this.aiUsageReportService = aiUsageReportService;
        this.learningEffectService = learningEffectService;
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
        return R.ok(aiUsageReportService.getReport(days));
    }

    @Operation(summary = "获取 AI 学习效果观察", description = "返回变式训练真实完成率，并对比同题阅读 AI 学习资产后的作答与基线作答；只表达观察性关联，不代表因果结论")
    @GetMapping("/learning-effect")
    public R<AiLearningEffectVO> getLearningEffect(
            @Parameter(description = "统计周期天数，默认 30；支持 1-90") @RequestParam(required = false) Integer days) {
        return R.ok(learningEffectService.getLearningEffect(days));
    }

    @Operation(summary = "获取未确认 AI 运营提醒", description = "返回已持久化且尚未确认的 AI 运营提醒")
    @GetMapping("/alerts")
    public R<List<AiUsageAlertVO>> getOpenAlerts(
            @Parameter(description = "返回数量，默认 20，最大 100") @RequestParam(required = false) Integer limit) {
        return R.ok(aiUsageReportService.getOpenAlerts(limit));
    }

    @Operation(summary = "确认 AI 运营提醒", description = "管理员确认提醒后记录处理人和确认时间")
    @PostMapping("/alerts/{id}/acknowledge")
    public R<AiUsageAlertVO> acknowledgeAlert(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(aiUsageReportService.acknowledgeAlert(id, userDetails.getUserId()));
    }
}
