package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户可访问试卷的查询入口。
 */
@Tag(name = "试卷", description = "用户可访问试卷查询接口")
@RestController
@RequestMapping("/api/exam/papers")
public class ExamPaperController {

    private final ExamPaperService examPaperService;

    public ExamPaperController(ExamPaperService examPaperService) {
        this.examPaperService = examPaperService;
    }

    @Operation(summary = "试卷列表", description = "获取已发布的试卷列表")
    @GetMapping
    public R<Page<ExamPaperVO>> getPublishedPapers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long courseId) {
        return R.ok(examPaperService.getAccessiblePublishedExamPaperPage(
                userDetails.getUserId(), pageNum, pageSize, courseId));
    }

    @Operation(summary = "试卷详情", description = "获取试卷详情，用于考试前预览")
    @GetMapping("/{id}")
    public R<ExamPaperVO> getPaperDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(examPaperService.getAccessiblePublishedExamPaperById(id, userDetails.getUserId()));
    }
}
