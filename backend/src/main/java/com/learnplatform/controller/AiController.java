package com.learnplatform.controller;

import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.AiRequest;
import com.learnplatform.dto.AiResponse;
import com.learnplatform.dto.QuestionLearningAssetVO;
import com.learnplatform.common.result.R;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiService;
import com.learnplatform.service.QuestionLearningAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * AI 控制器
 */
@Tag(name = "AI 功能", description = "AI 题目解析、变式题、复习建议、知识点总结")
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;
    private final QuestionLearningAssetService learningAssetService;
    private final Executor aiTaskExecutor;

    public AiController(AiService aiService,
                        QuestionLearningAssetService learningAssetService,
                        @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
        this.aiService = aiService;
        this.learningAssetService = learningAssetService;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    /**
     * AI 生成题目解析（带日志记录）
     */
    @Operation(summary = "题目解析", description = "AI 生成题目的详细解析")
    @PostMapping("/explanation")
    public R<AiResponse> generateExplanation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AiRequest request) {
        return R.ok(aiService.generateExplanation(request.getQuestionId(), userDetails.getUserId()));
    }

    /**
     * AI 生成变式题（带日志记录）
     */
    @Operation(summary = "变式题", description = "AI 基于原题生成变式题")
    @PostMapping("/variant")
    public R<AiResponse> generateVariant(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AiRequest request) {
        return R.ok(aiService.generateVariant(request.getQuestionId(), userDetails.getUserId()));
    }

    @Operation(summary = "流式题目解析", description = "通过 SSE 逐段返回 AI 题目解析")
    @PostMapping(value = "/explanation/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> generateExplanationStream(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AiRequest request) {
        Long userId = userDetails.getUserId();
        return stream(onContent -> aiService.generateExplanationStream(request.getQuestionId(), userId, onContent));
    }

    @Operation(summary = "流式变式题", description = "通过 SSE 逐段返回 AI 变式题")
    @PostMapping(value = "/variant/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> generateVariantStream(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AiRequest request) {
        Long userId = userDetails.getUserId();
        return stream(onContent -> aiService.generateVariantStream(request.getQuestionId(), userId, onContent));
    }

    /**
     * AI 生成复习建议
     */
    @Operation(summary = "复习建议", description = "AI 基于错题数据生成个性化复习建议")
    @PostMapping("/review-suggestion")
    public R<AiResponse> generateReviewSuggestion(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody(required = false) AiRequest request) {
        Long courseId = (request != null) ? request.getCourseId() : null;
        return R.ok(aiService.generateReviewSuggestion(userDetails.getUserId(), courseId));
    }

    @Operation(summary = "流式复习建议", description = "通过 SSE 逐段返回 AI 个性化复习建议")
    @PostMapping(value = "/review-suggestion/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> generateReviewSuggestionStream(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody(required = false) AiRequest request) {
        Long userId = userDetails.getUserId();
        Long courseId = (request != null) ? request.getCourseId() : null;
        return stream(onContent -> aiService.generateReviewSuggestionStream(userId, courseId, onContent));
    }

    /**
     * 查询当前用户今日 AI 调用用量
     */
    @Operation(summary = "AI 用量查询", description = "查询当前用户今日 AI 调用次数和每日配额")
    @GetMapping("/usage")
    public R<Map<String, Object>> getUsage(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        int[] usage = aiService.getDailyUsage(userDetails.getUserId());
        return R.ok(Map.of("todayCount", usage[0], "dailyQuota", usage[1]));
    }

    /**
     * AI 生成知识点总结（带日志记录）
     */
    @Operation(summary = "知识点总结", description = "AI 生成知识点的总结内容")
    @PostMapping("/summary")
    public R<AiResponse> generateSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AiRequest request) {
        return R.ok(aiService.generateSummary(request.getKnowledgePointId(), userDetails.getUserId()));
    }

    // ======================== AI 学习资产接口 ========================

    /**
     * 查询一道题的所有已缓存 AI 学习资产
     */
    @Operation(summary = "查询题目学习资产", description = "获取指定题目的所有已缓存 AI 学习资产")
    @GetMapping("/assets/{questionId}")
    public R<List<QuestionLearningAssetVO>> getAssets(@PathVariable Long questionId) {
        return R.ok(learningAssetService.getAssets(questionId));
    }

    /**
     * 同步生成或获取指定类型的 AI 学习资产（优先返回缓存）
     */
    @Operation(summary = "生成学习资产", description = "同步生成或获取指定类型的 AI 学习资产，有缓存则直接返回")
    @PostMapping("/asset/generate")
    public R<QuestionLearningAssetVO> generateAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> request) {
        Long questionId = Long.valueOf(request.get("questionId").toString());
        AiAssetType assetType = AiAssetType.valueOf(request.get("assetType").toString());
        return R.ok(learningAssetService.generateOrGetAsset(questionId, assetType, userDetails.getUserId()));
    }

    /**
     * 流式生成指定类型的 AI 学习资产（SSE）
     */
    @Operation(summary = "流式生成学习资产", description = "通过 SSE 流式生成 AI 学习资产，完成后自动缓存")
    @PostMapping(value = "/asset/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> generateAssetStream(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> request) {
        Long questionId = Long.valueOf(request.get("questionId").toString());
        AiAssetType assetType = AiAssetType.valueOf(request.get("assetType").toString());
        Long userId = userDetails.getUserId();
        return stream(onContent -> learningAssetService.generateAssetStream(questionId, assetType, userId, onContent));
    }

    /**
     * 提交 AI 学习资产反馈
     */
    @Operation(summary = "资产反馈", description = "对 AI 学习资产给出有帮助/无帮助反馈")
    @PostMapping("/asset/feedback")
    public R<Void> submitAssetFeedback(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> request) {
        Long questionId = Long.valueOf(request.get("questionId").toString());
        String assetType = request.get("assetType").toString();
        Boolean helpful = Boolean.valueOf(request.get("helpful").toString());
        String comment = request.get("comment") != null ? request.get("comment").toString() : null;
        learningAssetService.submitFeedback(questionId, assetType, userDetails.getUserId(), helpful, comment);
        return R.ok(null);
    }

    /**
     * 查询当前用户对某题某类型资产的反馈
     */
    @Operation(summary = "查询资产反馈", description = "查询当前用户对指定资产的反馈状态")
    @GetMapping("/asset/feedback/{questionId}/{assetType}")
    public R<Map<String, Object>> getAssetFeedback(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long questionId,
            @PathVariable String assetType) {
        var feedback = learningAssetService.getUserFeedback(questionId, assetType, userDetails.getUserId());
        if (feedback == null) {
            return R.ok(null);
        }
        return R.ok(Map.of(
                "helpful", feedback.getHelpful(),
                "comment", feedback.getComment() != null ? feedback.getComment() : ""
        ));
    }

    /**
     * 清除题目的 AI 学习资产缓存
     */
    @Operation(summary = "清除学习资产缓存", description = "删除指定题目的所有已缓存 AI 学习资产")
    @DeleteMapping("/assets/{questionId}")
    public R<Void> clearAssets(@PathVariable Long questionId) {
        learningAssetService.clearAssets(questionId);
        return R.ok(null);
    }

    private ResponseEntity<SseEmitter> stream(Consumer<Consumer<String>> generator) {
        SseEmitter emitter = new SseEmitter(120_000L);
        aiTaskExecutor.execute(() -> {
            try {
                generator.accept(content -> send(emitter, "content", Map.of("content", content)));
                send(emitter, "done", Map.of("source", "ai"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    send(emitter, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "AI 服务调用失败"));
                } catch (Exception ignored) {
                    // 客户端已断开时无需再次写入 SSE。
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
