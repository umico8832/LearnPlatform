package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamService;
import com.learnplatform.service.ExamPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端考试控制器
 */
@Tag(name = "考试", description = "用户端考试相关接口")
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
    @Operation(summary = "试卷列表", description = "获取已发布的试卷列表")
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
    @Operation(summary = "试卷详情", description = "获取试卷详情，用于考试前预览")
    @GetMapping("/papers/{id}")
    public R<ExamPaperVO> getPaperDetail(@PathVariable Long id) {
        return R.ok(examPaperService.getPublishedExamPaperById(id));
    }


    /**
     * 开始考试
     */
    @Operation(summary = "开始考试", description = "创建考试记录，开始考试")
    @PostMapping("/start/{paperId}")
    public R<ExamRecordVO> startExam(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paperId) {
        return R.ok(examService.startExam(paperId, userDetails.getUserId()));
    }


    /**
     * 提交考试
     */
    @Operation(summary = "提交考试", description = "提交考试答案，系统自动判分")
    @PostMapping("/submit")
    public R<ExamRecordVO> submitExam(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ExamSubmitRequest request) {
        return R.ok(examService.submitExam(request, userDetails.getUserId()));
    }


    /**
     * 获取考试结果
     */
    @Operation(summary = "考试结果", description = "获取考试成绩和答题详情")
    @GetMapping("/result/{recordId}")
    public R<ExamRecordVO> getExamResult(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long recordId) {
        return R.ok(examService.getExamResult(recordId, userDetails.getUserId()));
    }


    /**
     * 获取我的考试记录
     */
    @Operation(summary = "考试记录", description = "分页获取当前用户的考试记录")
    @GetMapping("/records")
    public R<Page<ExamRecordVO>> getMyExamRecords(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(examService.getExamList(userDetails.getUserId(), pageNum, pageSize));
    }
}
