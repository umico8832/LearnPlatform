package com.learnplatform.controller;

import com.learnplatform.dto.AiRequest;
import com.learnplatform.dto.AiResponse;
import com.learnplatform.common.result.R;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiService;
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
    private final Executor aiTaskExecutor;

    public AiController(AiService aiService, @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
        this.aiService = aiService;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    /**
     * AI 生成题目解析
     */
    @Operation(summary = "题目解析", description = "AI 生成题目的详细解析")
    @PostMapping("/explanation")
    public R<AiResponse> generateExplanation(@RequestBody AiRequest request) {
        return R.ok(aiService.generateExplanation(request.getQuestionId()));
    }

    /**
     * AI 生成变式题
     */
    @Operation(summary = "变式题", description = "AI 基于原题生成变式题")
    @PostMapping("/variant")
    public R<AiResponse> generateVariant(@RequestBody AiRequest request) {
        return R.ok(aiService.generateVariant(request.getQuestionId()));
    }

    @Operation(summary = "流式题目解析", description = "通过 SSE 逐段返回 AI 题目解析")
    @PostMapping(value = "/explanation/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> generateExplanationStream(@RequestBody AiRequest request) {
        return stream(onContent -> aiService.generateExplanationStream(request.getQuestionId(), onContent));
    }

    @Operation(summary = "流式变式题", description = "通过 SSE 逐段返回 AI 变式题")
    @PostMapping(value = "/variant/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> generateVariantStream(@RequestBody AiRequest request) {
        return stream(onContent -> aiService.generateVariantStream(request.getQuestionId(), onContent));
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

    /**
     * AI 生成知识点总结
     */
    @Operation(summary = "知识点总结", description = "AI 生成知识点的总结内容")
    @PostMapping("/summary")
    public R<AiResponse> generateSummary(@RequestBody AiRequest request) {
        return R.ok(aiService.generateSummary(request.getKnowledgePointId()));
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
