package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.KnowledgeGraphVO;
import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.dto.LearningPathVO;
import com.learnplatform.dto.SimilarQuestionVO;
import com.learnplatform.dto.LearningReportVO;
import com.learnplatform.dto.StatisticsVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.KnowledgeGraphService;
import com.learnplatform.service.LearningDiagnosisService;
import com.learnplatform.service.LearningPathService;
import com.learnplatform.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.learnplatform.dto.AiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

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
    private final Executor aiTaskExecutor;

    public StatisticsController(StatisticsService statisticsService,
                                LearningPathService learningPathService,
                                KnowledgeGraphService knowledgeGraphService,
                                LearningDiagnosisService learningDiagnosisService,
                                @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
        this.statisticsService = statisticsService;
        this.learningPathService = learningPathService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.learningDiagnosisService = learningDiagnosisService;
        this.aiTaskExecutor = aiTaskExecutor;
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

    // ======================== AI 个性化学习建议 ========================

    @Operation(summary = "AI 学习建议", description = "基于学习诊断数据，AI 生成个性化学习建议")
    @PostMapping("/ai-advice")
    public R<AiResponse> getAiAdvice(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String content = learningDiagnosisService.generateAiAdvice(userDetails.getUserId());
        return R.ok(new AiResponse(content, "ai"));
    }

    @Operation(summary = "流式 AI 学习建议", description = "通过 SSE 流式返回 AI 个性化学习建议")
    @PostMapping(value = "/ai-advice/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> getAiAdviceStream(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getUserId();
        SseEmitter emitter = new SseEmitter(120_000L);
        aiTaskExecutor.execute(() -> {
            try {
                learningDiagnosisService.generateAiAdviceStream(userId,
                        content -> sendSse(emitter, "content", Map.of("content", content)));
                sendSse(emitter, "done", Map.of("source", "ai"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    sendSse(emitter, "error", Map.of("message",
                            e.getMessage() != null ? e.getMessage() : "AI 服务调用失败"));
                } catch (Exception ignored) {
                }
                emitter.complete();
            }
        });
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    // ======================== 相似题推荐 ========================

    @Operation(summary = "相似题推荐", description = "根据指定题目推荐同知识点、同题型、同难度的相似题目，用于错题巩固")
    @GetMapping("/similar-questions")
    public R<SimilarQuestionVO> getSimilarQuestions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long questionId,
            @RequestParam(defaultValue = "5") int limit) {
        return R.ok(learningDiagnosisService.findSimilarQuestions(
                userDetails.getUserId(), questionId, limit));
    }

    private void sendSse(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new IllegalStateException("SSE 连接已断开", e);
        }
    }
}
