package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.AiResponse;
import com.learnplatform.dto.ReviewContextVO;
import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.dto.ReviewStatsVO;
import com.learnplatform.dto.ReviewSubmitRequest;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiService;
import com.learnplatform.service.SpacedRepetitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 间隔重复复习控制器
 */
@Tag(name = "间隔重复复习", description = "基于 SM-2 算法的智能复习计划")
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final SpacedRepetitionService spacedRepetitionService;
    private final AiService aiService;
    private final Executor aiTaskExecutor;

    public ReviewController(SpacedRepetitionService spacedRepetitionService,
                            AiService aiService,
                            @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
        this.spacedRepetitionService = spacedRepetitionService;
        this.aiService = aiService;
        this.aiTaskExecutor = aiTaskExecutor;
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
            @Parameter(description = "目标题目ID") @RequestParam(required = false) Long questionId,
            @Parameter(description = "知识点ID筛选") @RequestParam(required = false) Long knowledgePointId,
            @Parameter(description = "数量限制") @RequestParam(required = false, defaultValue = "20") Integer limit,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(spacedRepetitionService.getDueReviewCards(
                userDetails.getUserId(), courseId, questionId, knowledgePointId, limit));
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

    @Operation(summary = "同步错题本到复习计划（未掌握/部分掌握的错题自动加入）")
    @PostMapping("/sync-wrong-questions")
    public R<Map<String, Object>> syncWrongQuestions(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        int synced = spacedRepetitionService.syncWrongQuestionsToReviewPlan(userDetails.getUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("syncedCount", synced);
        return R.ok(result);
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

    // ========== AI 复习建议 ==========

    @Operation(summary = "AI 复习建议（同步）", description = "基于间隔重复复习统计数据生成个性化 AI 复习建议")
    @PostMapping("/ai-suggestion")
    public R<AiResponse> getAiSuggestion(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getUserId();
        ReviewContextVO ctx = spacedRepetitionService.buildReviewContext(userId);
        return R.ok(aiService.generateReviewBasedSuggestionWithContext(userId, ctx));
    }

    @Operation(summary = "AI 复习建议（流式 SSE）", description = "通过 SSE 流式返回基于复习数据的个性化 AI 建议")
    @PostMapping(value = "/ai-suggestion/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> getAiSuggestionStream(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getUserId();
        ReviewContextVO ctx = spacedRepetitionService.buildReviewContext(userId);

        SseEmitter emitter = new SseEmitter(120_000L);
        aiTaskExecutor.execute(() -> {
            try {
                aiService.generateReviewBasedSuggestionStreamWithContext(userId, ctx,
                        content -> send(emitter, "content", Map.of("content", content)));
                send(emitter, "done", Map.of("source", "ai"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    send(emitter, "error", Map.of("message",
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

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new IllegalStateException("SSE 连接已断开", e);
        }
    }
}
