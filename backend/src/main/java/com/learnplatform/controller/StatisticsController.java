package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.StatisticsVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.StatisticsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统计控制器
 */
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    public R<StatisticsVO> getOverview(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(statisticsService.getUserStatistics(userDetails.getUserId()));
    }

    @GetMapping("/daily-trend")
    public R<List<Map<String, Object>>> getDailyTrend(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(statisticsService.getDailyTrend(userDetails.getUserId()));
    }

    @GetMapping("/course-stats")
    public R<List<Map<String, Object>>> getCourseStats(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(statisticsService.getCourseStats(userDetails.getUserId()));
    }
}