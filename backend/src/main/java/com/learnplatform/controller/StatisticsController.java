package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.StatisticsVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统计控制器
 */
@Tag(name = "统计", description = "用户学习数据统计接口")
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Operation(summary = "学习概览", description = "获取当前用户的学习统计数据（总刷题、正确率等）")
    @GetMapping("/overview")
    public R<StatisticsVO> getOverview(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(statisticsService.getUserStatistics(userDetails.getUserId()));
    }

    @Operation(summary = "每日趋势", description = "获取近7天的刷题趋势数据")
    @GetMapping("/daily-trend")
    public R<List<Map<String, Object>>> getDailyTrend(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(statisticsService.getDailyTrend(userDetails.getUserId()));
    }

    @Operation(summary = "课程统计", description = "获取各课程的正确率统计数据")
    @GetMapping("/course-stats")
    public R<List<Map<String, Object>>> getCourseStats(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(statisticsService.getCourseStats(userDetails.getUserId()));
    }
}