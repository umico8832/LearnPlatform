package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.KnowledgeGraphVO;
import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.dto.LearningPathVO;
import com.learnplatform.dto.LearningReportVO;
import com.learnplatform.dto.StatisticsVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.KnowledgeGraphService;
import com.learnplatform.service.LearningDiagnosisService;
import com.learnplatform.service.LearningPathService;
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
    private final LearningPathService learningPathService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final LearningDiagnosisService learningDiagnosisService;

    public StatisticsController(StatisticsService statisticsService,
                                LearningPathService learningPathService,
                                KnowledgeGraphService knowledgeGraphService,
                                LearningDiagnosisService learningDiagnosisService) {
        this.statisticsService = statisticsService;
        this.learningPathService = learningPathService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.learningDiagnosisService = learningDiagnosisService;
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

    @Operation(summary = "个人学习报告", description = "获取当前用户的月度学习报告（本月刷题量、正确率趋势、错题变化、考试成绩等）")
    @GetMapping("/learning-report")
    public R<LearningReportVO> getLearningReport(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(statisticsService.getLearningReport(userDetails.getUserId()));
    }

    @Operation(summary = "学习路径推荐", description = "根据用户在各知识点的练习表现，生成个性化学习路径推荐（可按课程筛选）")
    @GetMapping("/learning-path")
    public R<LearningPathVO> getLearningPath(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long courseId) {
        return R.ok(learningPathService.getLearningPath(userDetails.getUserId(), courseId));
    }

    @Operation(summary = "知识图谱", description = "获取知识图谱数据，包含知识点关系和用户练习表现（可按课程筛选）")
    @GetMapping("/knowledge-graph")
    public R<KnowledgeGraphVO> getKnowledgeGraph(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long courseId) {
        return R.ok(knowledgeGraphService.getKnowledgeGraph(userDetails.getUserId(), courseId));
    }

    @Operation(summary = "学习诊断", description = "获取用户学习诊断数据，包含知识点薄弱诊断、错因分析、学习习惯和每日推荐题目")
    @GetMapping("/learning-diagnosis")
    public R<LearningDiagnosisVO> getLearningDiagnosis(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(learningDiagnosisService.getDiagnosis(userDetails.getUserId()));
    }
}
