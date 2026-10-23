package com.learnplatform.controller;

import tools.jackson.databind.JsonNode;
import com.learnplatform.common.result.R;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.understanding.QuestionUnderstandingService;
import com.learnplatform.service.HttpIdempotencyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/understanding")
public class QuestionUnderstandingController {
    private final QuestionUnderstandingService service;
    private final HttpIdempotencyService idempotency;

    public QuestionUnderstandingController(QuestionUnderstandingService service, HttpIdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @GetMapping("/questions/{questionId}")
    public R<Map<String, Object>> question(@PathVariable long questionId, @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.question(user.getUserId(), questionId));
    }

    @PostMapping("/questions/{questionId}/attempts")
    public R<Map<String, Object>> submit(@PathVariable long questionId, @RequestBody JsonNode body,
                                          @AuthenticationPrincipal CustomUserDetails user,
                                          @RequestHeader("Idempotency-Key") String requestKey) {
        Map<String, Object> response = idempotency.execute(user.getUserId(), "understanding.attempt", requestKey,
                Map.of("questionId", questionId, "body", body), Map.class,
                () -> service.submit(user.getUserId(), questionId, body));
        return R.ok(response);
    }

    @PostMapping("/attempts/{attemptId}/solution/reveal")
    public R<Map<String, Object>> reveal(@PathVariable long attemptId, @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.reveal(user.getUserId(), attemptId));
    }

    @GetMapping("/attempts/{attemptId}/tutor/next")
    public R<Map<String, Object>> tutorNext(@PathVariable long attemptId,
                                            @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.tutorNext(user.getUserId(), attemptId));
    }

    @PostMapping("/attempts/{attemptId}/tutor/hints/next")
    public R<Map<String, Object>> tutorHint(@PathVariable long attemptId, @RequestBody JsonNode body,
                                            @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.tutorHint(user.getUserId(), attemptId, body));
    }

    @PostMapping("/attempts/{attemptId}/tutor/viz-views")
    public R<Map<String, Object>> tutorVizView(@PathVariable long attemptId, @RequestBody JsonNode body,
                                               @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.tutorVizView(user.getUserId(), attemptId, body));
    }

    @GetMapping("/attempts/{attemptId}/variant")
    public R<Map<String, Object>> variant(@PathVariable long attemptId, @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.variant(user.getUserId(), attemptId));
    }

    @PostMapping("/variants/{variantId}/attempts")
    public R<Map<String, Object>> submitVariant(@PathVariable long variantId, @RequestBody JsonNode body,
                                                 @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.submitVariant(user.getUserId(), variantId, body));
    }
}
