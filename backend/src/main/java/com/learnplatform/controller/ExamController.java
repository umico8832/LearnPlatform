package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamService;
import com.learnplatform.service.ExamPaperService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端考试控制器
 */
@RestController
@RequestMapping("/api/exam")
public class ExamController {

    private final ExamService examService;
    private final ExamPaperService examPaperService;

    public ExamController(ExamService examService, ExamPaperService examPaperService) {
        this.examService = examService;
        this.examPaperService = examPaperService;
    }

    /**
     * 获取已发布试卷列表
     */
    @GetMapping("/papers")
    public R<Page<ExamPaperVO>> getPublishedPapers(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long courseId) {
        return R.ok(examPaperService.getExamPaperPage(pageNum, pageSize, courseId, 1));
    }

    /**
     * 获取试卷详情（考试前查看）
     */
    @GetMapping("/papers/{id}")
    public R<ExamPaperVO> getPaperDetail(@PathVariable Long id) {
        return R.ok(examPaperService.getExamPaperById(id));
    }

    /**
     * 开始考试
     */
    @PostMapping("/start/{paperId}")
    public R<ExamRecordVO> startExam(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paperId) {
        return R.ok(examService.startExam(paperId, userDetails.getUserId()));
    }

    /**
     * 提交考试
     */
    @PostMapping("/submit")
    public R<ExamRecordVO> submitExam(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody ExamSubmitRequest request) {
        return R.ok(examService.submitExam(request, userDetails.getUserId()));
    }

    /**
     * 获取考试结果
     */
    @GetMapping("/result/{recordId}")
    public R<ExamRecordVO> getExamResult(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long recordId) {
        return R.ok(examService.getExamResult(recordId, userDetails.getUserId()));
    }

    /**
     * 获取我的考试记录
     */
    @GetMapping("/records")
    public R<Page<ExamRecordVO>> getMyExamRecords(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(examService.getExamList(userDetails.getUserId(), pageNum, pageSize));
    }
}