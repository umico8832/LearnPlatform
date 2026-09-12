package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.AiVariantReviewRequest;
import com.learnplatform.dto.AiVariantReviewVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiVariantReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-AI变式题审查", description = "审查结构化 AI 变式题并发布为正式题目")
@RestController
@RequestMapping("/api/admin/ai-variant-reviews")
public class AdminAiVariantReviewController {
    private final AiVariantReviewService service;

    public AdminAiVariantReviewController(AiVariantReviewService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询 AI 变式题审查队列")
    @GetMapping
    public R<Page<AiVariantReviewVO>> list(
            @RequestParam(defaultValue = "PENDING") String reviewStatus,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.list(reviewStatus, pageNum, pageSize));
    }

    @Operation(summary = "审查 AI 变式题")
    @PostMapping("/{variantId}")
    public R<AiVariantReviewVO> review(
            @PathVariable Long variantId,
            @Valid @RequestBody AiVariantReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(service.review(variantId, request, userDetails.getUserId()));
    }
}
