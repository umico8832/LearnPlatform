package com.learnplatform.controller;

import com.learnplatform.dto.AiRequest;
import com.learnplatform.dto.AiResponse;
import com.learnplatform.common.result.R;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * AI 控制器
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * AI 生成题目解析
     */
    @PostMapping("/explanation")
    public R<AiResponse> generateExplanation(@RequestBody AiRequest request) {
        return R.ok(aiService.generateExplanation(request.getQuestionId()));
    }

    /**
     * AI 生成变式题
     */
    @PostMapping("/variant")
    public R<AiResponse> generateVariant(@RequestBody AiRequest request) {
        return R.ok(aiService.generateVariant(request.getQuestionId()));
    }

    /**
     * AI 生成复习建议
     */
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
    @PostMapping("/summary")
    public R<AiResponse> generateSummary(@RequestBody AiRequest request) {
        return R.ok(aiService.generateSummary(request.getKnowledgePointId()));
    }
}