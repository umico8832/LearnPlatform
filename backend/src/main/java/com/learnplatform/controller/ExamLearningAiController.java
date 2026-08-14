package com.learnplatform.controller;

import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamLearningAiService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/exam/learning-sessions")
public class ExamLearningAiController {

    private final ExamLearningAiService service;
    private final Executor aiTaskExecutor;

    public ExamLearningAiController(ExamLearningAiService service,
                                    @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
        this.service = service;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    @PostMapping(value = "/{sessionId}/questions/{questionId}/ai/{assistanceType}/stream",
            produces = "text/event-stream")
    public ResponseEntity<SseEmitter> streamAssistance(
            @PathVariable Long sessionId,
            @PathVariable Long questionId,
            @PathVariable String assistanceType,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SseEmitter emitter = new SseEmitter(300_000L);
        aiTaskExecutor.execute(() -> {
            try {
                service.streamAssistance(sessionId, questionId, assistanceType, userDetails.getUserId(),
                        content -> send(emitter, "content", Map.of("content", content)));
                send(emitter, "done", Map.of("source", "ai"));
                emitter.complete();
            } catch (Exception exception) {
                try {
                    send(emitter, "error", Map.of("message", exception.getMessage() != null
                            ? exception.getMessage() : "AI 服务调用失败"));
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
        } catch (IOException exception) {
            throw new IllegalStateException("SSE 连接已断开", exception);
        }
    }
}
