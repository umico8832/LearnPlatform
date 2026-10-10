package com.learnplatform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnplatform.common.result.R;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.understanding.QuestionUnderstandingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/understanding")
public class QuestionUnderstandingController {
    private final QuestionUnderstandingService service;

    public QuestionUnderstandingController(QuestionUnderstandingService service) { this.service = service; }

    @GetMapping("/questions/{questionId}")
    public R<Map<String, Object>> question(@PathVariable long questionId, @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.question(user.getUserId(), questionId));
    }

    @PostMapping("/questions/{questionId}/attempts")
    public R<Map<String, Object>> submit(@PathVariable long questionId, @RequestBody JsonNode body,
                                          @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.submit(user.getUserId(), questionId, body));
    }

    @PostMapping("/attempts/{attemptId}/solution/reveal")
    public R<Map<String, Object>> reveal(@PathVariable long attemptId, @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.reveal(user.getUserId(), attemptId));
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
