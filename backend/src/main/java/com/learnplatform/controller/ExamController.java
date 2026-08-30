package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamService;
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

/**
 * 用户端限时考试会话入口。
 */
@Tag(name = "考试", description = "用户端限时考试会话接口")
@RestController
@RequestMapping("/api/exam")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @Operation(summary = "开始考试", description = "创建考试记录，开始考试")
    @PostMapping("/start/{paperId}")
    public R<ExamRecordVO> startExam(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paperId) {
        return R.ok(examService.startExam(paperId, userDetails.getUserId()));
    }

    @Operation(summary = "获取本人限时考试会话")
    @GetMapping("/records/{recordId}/session")
    public R<ExamRecordVO> getExamSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long recordId) {
        return R.ok(examService.getExamSession(recordId, userDetails.getUserId()));
    }

    @Operation(summary = "提交考试", description = "提交考试答案，系统自动判分")
    @PostMapping("/submit")
    public R<ExamRecordVO> submitExam(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ExamSubmitRequest request) {
        return R.ok(examService.submitExam(request, userDetails.getUserId()));
    }

    @Operation(summary = "考试结果", description = "获取考试成绩和答题详情")
    @GetMapping("/result/{recordId}")
    public R<ExamRecordVO> getExamResult(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long recordId) {
        return R.ok(examService.getExamResult(recordId, userDetails.getUserId()));
    }

    @Operation(summary = "考试记录", description = "分页获取当前用户的考试记录")
    @GetMapping("/records")
    public R<Page<ExamRecordVO>> getMyExamRecords(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(examService.getExamList(userDetails.getUserId(), pageNum, pageSize));
    }
}
