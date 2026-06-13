package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.ExamPaperCreateRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamPaperService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端试卷控制器
 */
@RestController
@RequestMapping("/api/admin/exam-papers")
public class AdminExamController {

    private final ExamPaperService examPaperService;

    public AdminExamController(ExamPaperService examPaperService) {
        this.examPaperService = examPaperService;
    }

    @GetMapping
    public R<Page<ExamPaperVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer status) {
        return R.ok(examPaperService.getExamPaperPage(pageNum, pageSize, courseId, status));
    }

    @GetMapping("/{id}")
    public R<ExamPaperVO> detail(@PathVariable Long id) {
        return R.ok(examPaperService.getExamPaperById(id));
    }

    @PostMapping
    public R<ExamPaperVO> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody ExamPaperCreateRequest request) {
        return R.ok(examPaperService.createExamPaper(request, userDetails.getUserId()));
    }

    @PutMapping("/{id}")
    public R<ExamPaperVO> update(@PathVariable Long id, @RequestBody ExamPaperCreateRequest request) {
        return R.ok(examPaperService.updateExamPaper(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        examPaperService.deleteExamPaper(id);
        return R.ok(null);
    }

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        examPaperService.publishExamPaper(id);
        return R.ok(null);
    }
}