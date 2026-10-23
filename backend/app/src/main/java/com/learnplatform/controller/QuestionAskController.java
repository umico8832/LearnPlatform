package com.learnplatform.controller;

import tools.jackson.databind.JsonNode;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.R;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.dto.QuestionAskResponse;
import com.learnplatform.service.understanding.QuestionAskService;
import com.learnplatform.service.ai.AiTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/understanding/attempts/{attemptId}/ask")
public class QuestionAskController {
    private final QuestionAskService service;
    private final AiTaskExecutor executor;

    public QuestionAskController(QuestionAskService service, @Qualifier("aiTaskExecutor") AiTaskExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    @GetMapping
    public R<QuestionAskResponse.Status> status(@PathVariable long attemptId,
                                         @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.status(user.getUserId(), attemptId));
    }

    @DeleteMapping("/{requestId}")
    public R<Map<String, Object>> stop(@PathVariable long attemptId, @PathVariable String requestId,
                                       @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.stop(user.getUserId(), attemptId, requestId));
    }

    @PostMapping("/stream")
    public ResponseEntity<SseEmitter> stream(@PathVariable long attemptId, @RequestBody JsonNode body,
                              @AuthenticationPrincipal CustomUserDetails user) {
        var reservation = executor.reserve();
        QuestionAskService.Job job;
        try { job = service.prepare(user.getUserId(), attemptId, body); }
        catch (RuntimeException exception) {
            reservation.close();
            throw exception;
        }
        var emitter = new SseEmitter(job.timeoutMillis());
        var ended = new AtomicBoolean();
        var executing = new AtomicBoolean();
        var writes = new Object();
        var heartbeat = new AtomicReference<ScheduledFuture<?>>();
        Runnable clearHeartbeat = () -> {
            var scheduled = heartbeat.get();
            if (scheduled != null) { scheduled.cancel(false); }
        };
        Runnable stop = () -> {
            clearHeartbeat.run();
            if (!ended.compareAndSet(false, true)) { return; }
            job.cancellation().cancel();
            reservation.cancel();
            try { service.cancel(job); }
            catch (RuntimeException ignored) { /* Cancellation still reaches the local upstream request. */ }
        };
        emitter.onCompletion(stop);
        emitter.onTimeout(stop);
        emitter.onError(error -> stop.run());
        reservation.onShutdown(() -> {
            clearHeartbeat.run();
            if (!ended.compareAndSet(false, true)) { return; }
            job.cancellation().cancel();
            try {
                if (executing.get()) {
                    service.cancel(job);
                } else {
                    service.reject(job);
                }
            } finally { emitter.complete(); }
        });
        reservation.onLeaseLost(() -> {
            clearHeartbeat.run();
            if (!ended.compareAndSet(false, true)) { return; }
            job.cancellation().cancel();
            try { service.cancel(job); }
            catch (RuntimeException ignored) { /* The worker interrupt still stops local upstream I/O. */ }
            finally { emitter.complete(); }
        });
        try {
            send(emitter, "status", Map.of("state", "processing"));
            heartbeat.set(executor.heartbeat(() -> {
                synchronized (writes) {
                    if (ended.get()) { return; }
                    try { send(emitter, "heartbeat", Map.of()); }
                    catch (RuntimeException exception) { stop.run(); }
                }
            }));
            reservation.execute(() -> {
                executing.set(true);
                try {
                    var done = service.execute(job, text -> {
                        synchronized (writes) {
                            if (ended.get()) { throw new BusinessException(ResultCode.ASK_CANCELLED); }
                            send(emitter, "content", Map.of("content", text));
                        }
                    });
                    synchronized (writes) {
                        if (!ended.getAndSet(true)) { send(emitter, "done", done); }
                    }
                } catch (BusinessException exception) {
                    synchronized (writes) {
                        if (!ended.getAndSet(true)) { error(emitter, exception.getCode(), exception.getMessage()); }
                    }
                } catch (RuntimeException exception) {
                    synchronized (writes) {
                        if (!ended.getAndSet(true)) { error(emitter, 1012, ResultCode.ASK_UPSTREAM.getMessage()); }
                    }
                } finally {
                    ended.set(true);
                    clearHeartbeat.run();
                    emitter.complete();
                }
            });
        } catch (RuntimeException exception) {
            ended.set(true);
            clearHeartbeat.run();
            job.cancellation().cancel();
            reservation.close();
            service.reject(job);
            error(emitter, 1007, ResultCode.RATE_LIMITED.getMessage());
            emitter.complete();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> businessError(BusinessException exception) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(R.fail(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> invalidBody() {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(R.fail(ResultCode.VALIDATION_ERROR));
    }

    private void error(SseEmitter emitter, int code, String message) {
        try { send(emitter, "error", Map.of("code", code, "message", message)); }
        catch (RuntimeException ignored) { /* The client may already have disconnected. */ }
    }

    private void send(SseEmitter emitter, String event, Object value) {
        try { emitter.send(SseEmitter.event().name(event).data(value, MediaType.APPLICATION_JSON)); }
        catch (IOException exception) { throw new IllegalStateException("SSE closed"); }
    }
}
