package com.learnplatform.controller;

import com.learnplatform.dto.AiRequest;
import com.learnplatform.dto.AiResponse;
import com.learnplatform.common.result.R;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * AI 控制器
 */
@Tag(name = "AI 功能", description = "AI 题目解析、变式题、复习建议、知识点总结")
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
}