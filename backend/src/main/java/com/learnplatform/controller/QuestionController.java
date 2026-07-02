package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.QuestionCorrectionReportRequest;
import com.learnplatform.dto.QuestionCorrectionReportVO;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.QuestionCorrectionReportService;
import com.learnplatform.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端题目控制器
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionCorrectionReportService correctionReportService;

    public QuestionController(QuestionService questionService,
                              QuestionCorrectionReportService correctionReportService) {
        this.questionService = questionService;
        this.correctionReportService = correctionReportService;
    }

    /**
     * 分页查询题目（用户端，仅启用状态）
     */
    @GetMapping
    public R<Page<QuestionVO>> listQuestions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer difficulty) {
        return R.ok(questionService.getEnabledQuestionPage(pageNum, pageSize,
                questionType, courseId, difficulty));
    }

    /**
     * 获取题目详情
     */
    @GetMapping("/{id}")
    public R<QuestionVO> getQuestion(@PathVariable Long id) {
        return R.ok(questionService.getEnabledQuestionById(id));
    }

    /**
     * 提交题目纠错反馈
     */
    @PostMapping("/{id}/correction-reports")
    public R<QuestionCorrectionReportVO> submitCorrectionReport(
            @PathVariable Long id,
            @Valid @RequestBody QuestionCorrectionReportRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(correctionReportService.submitReport(id, request, userDetails.getUserId()));
    }

    /**
     * 当前用户的题目纠错反馈
     */
    @GetMapping("/correction-reports/my")
    public R<Page<QuestionCorrectionReportVO>> myCorrectionReports(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(correctionReportService.getMyReports(userDetails.getUserId(), pageNum, pageSize, status));
    }
}
