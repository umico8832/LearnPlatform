package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.AdminStatisticsVO;
import com.learnplatform.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端统计控制器
 */
@Tag(name = "管理端统计", description = "平台运营数据概览")
@RestController
@RequestMapping("/api/admin/statistics")
public class AdminStatisticsController {

    private final StatisticsService statisticsService;

    public AdminStatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Operation(summary = "平台统计概览", description = "获取用户、题目、试卷和活跃度统计")
    @GetMapping("/overview")
    public R<AdminStatisticsVO> getOverview() {
        return R.ok(statisticsService.getAdminStatistics());
    }
}
