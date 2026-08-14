package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.dto.ExamLearningAnswerRequest;
import com.learnplatform.dto.ExamLearningAnswerResultVO;
import com.learnplatform.dto.ExamLearningSessionVO;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamImportConfirmRequest;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.dto.PrivateExamImportRequest;
import com.learnplatform.dto.PrivateExamSourceVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamService;
import com.learnplatform.service.ExamPaperService;
import com.learnplatform.service.ExamPaperLearningService;
import com.learnplatform.service.PrivateExamImportService;
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
    private final ExamPaperLearningService examPaperLearningService;
    private final PrivateExamImportService privateExamImportService;

    public ExamController(ExamService examService, ExamPaperService examPaperService,
                          ExamPaperLearningService examPaperLearningService,
                          PrivateExamImportService privateExamImportService) {
        this.examService = examService;
        this.examPaperService = examPaperService;
        this.examPaperLearningService = examPaperLearningService;
        this.privateExamImportService = privateExamImportService;
    }


    /**
     * 获取已发布试卷列表
     */
    @Operation(summary = "试卷列表", description = "获取已发布的试卷列表")
    @GetMapping("/papers")
    public R<Page<ExamPaperVO>> getPublishedPapers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long courseId) {
        return R.ok(examPaperService.getAccessiblePublishedExamPaperPage(
                userDetails.getUserId(), pageNum, pageSize, courseId));
    }


    /**
     * 获取试卷详情（考试前查看）
     */
    @Operation(summary = "试卷详情", description = "获取试卷详情，用于考试前预览")
    @GetMapping("/papers/{id}")
    public R<ExamPaperVO> getPaperDetail(@PathVariable Long id,
                                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(examPaperService.getAccessiblePublishedExamPaperById(id, userDetails.getUserId()));
    }

    @PostMapping("/private-papers/import/preview")
    public R<PrivateExamImportPreviewVO> previewPrivatePaper(
            @Valid @RequestBody PrivateExamImportRequest request) {
        return R.ok(privateExamImportService.preview(request));
    }

    @PostMapping("/private-papers/import/confirm")
    public R<ExamPaperVO> confirmPrivatePaper(
            @Valid @RequestBody PrivateExamImportConfirmRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamImportService.confirm(request, userDetails.getUserId()));
    }

    @GetMapping("/private-papers/{paperId}/source")
    public R<PrivateExamSourceVO> getPrivatePaperSource(
            @PathVariable Long paperId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamImportService.getSource(paperId, userDetails.getUserId()));
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

    @Operation(summary = "获取本人限时考试会话")
    @GetMapping("/records/{recordId}/session")
    public R<ExamRecordVO> getExamSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long recordId) {
        return R.ok(examService.getExamSession(recordId, userDetails.getUserId()));
    }

    @Operation(summary = "开始或恢复试卷学习会话")
    @PostMapping("/papers/{paperId}/learning-sessions")
    public R<ExamLearningSessionVO> startLearningSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paperId) {
        return R.ok(examPaperLearningService.startSession(paperId, userDetails.getUserId()));
    }

    @Operation(summary = "获取本人试卷学习会话")
    @GetMapping("/learning-sessions/{sessionId}")
    public R<ExamLearningSessionVO> getLearningSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId) {
        return R.ok(examPaperLearningService.getSession(sessionId, userDetails.getUserId()));
    }

    @Operation(summary = "提交试卷学习逐题答案")
    @PostMapping("/learning-sessions/{sessionId}/answers")
    public R<ExamLearningAnswerResultVO> submitLearningAnswer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId,
            @Valid @RequestBody ExamLearningAnswerRequest request) {
        return R.ok(examPaperLearningService.submitAnswer(sessionId, request, userDetails.getUserId()));
    }

    @Operation(summary = "完成本轮试卷学习")
    @PostMapping("/learning-sessions/{sessionId}/complete")
    public R<ExamLearningSessionVO> completeLearningSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId) {
        return R.ok(examPaperLearningService.completeSession(sessionId, userDetails.getUserId()));
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
