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
import com.learnplatform.dto.PrivateExamDraftConfirmRequest;
import com.learnplatform.dto.PrivateExamDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDraftReviewRequest;
import com.learnplatform.dto.PrivateExamDraftVO;
import com.learnplatform.dto.PrivateExamPdfConfirmRequest;
import com.learnplatform.dto.PrivateExamPdfDraftCreateRequest;
import com.learnplatform.dto.PrivateExamPdfRequest;
import com.learnplatform.dto.PrivateExamDocxConfirmRequest;
import com.learnplatform.dto.PrivateExamDocxDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDocxRequest;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamService;
import com.learnplatform.service.ExamPaperService;
import com.learnplatform.service.ExamPaperLearningService;
import com.learnplatform.service.PrivateExamImportService;
import com.learnplatform.service.PrivateExamDraftService;
import com.learnplatform.service.PrivateExamContentLifecycleService;
import com.learnplatform.service.PrivateExamPdfImportService;
import com.learnplatform.service.PrivateExamDocxImportService;
import com.learnplatform.service.PrivateExamSourceFile;
import com.learnplatform.service.PrivateExamSourceFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.nio.charset.StandardCharsets;

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
    private final PrivateExamDraftService privateExamDraftService;
    private final PrivateExamContentLifecycleService privateExamContentLifecycleService;
    private final PrivateExamPdfImportService privateExamPdfImportService;
    private final PrivateExamDocxImportService privateExamDocxImportService;
    private final PrivateExamSourceFileService privateExamSourceFileService;

    public ExamController(ExamService examService, ExamPaperService examPaperService,
                          ExamPaperLearningService examPaperLearningService,
                          PrivateExamImportService privateExamImportService,
                          PrivateExamDraftService privateExamDraftService,
                          PrivateExamContentLifecycleService privateExamContentLifecycleService,
                          PrivateExamPdfImportService privateExamPdfImportService,
                          PrivateExamDocxImportService privateExamDocxImportService,
                          PrivateExamSourceFileService privateExamSourceFileService) {
        this.examService = examService;
        this.examPaperService = examPaperService;
        this.examPaperLearningService = examPaperLearningService;
        this.privateExamImportService = privateExamImportService;
        this.privateExamDraftService = privateExamDraftService;
        this.privateExamContentLifecycleService = privateExamContentLifecycleService;
        this.privateExamPdfImportService = privateExamPdfImportService;
        this.privateExamDocxImportService = privateExamDocxImportService;
        this.privateExamSourceFileService = privateExamSourceFileService;
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

    @PostMapping(value = "/private-papers/import/pdf/preview", consumes = "multipart/form-data")
    public R<PrivateExamImportPreviewVO> previewPrivatePaperPdf(
            @Valid @RequestPart("metadata") PrivateExamPdfRequest metadata,
            @RequestPart("file") MultipartFile file) {
        return R.ok(privateExamPdfImportService.preview(metadata, file));
    }

    @PostMapping(value = "/private-papers/import/pdf/confirm", consumes = "multipart/form-data")
    public R<ExamPaperVO> confirmPrivatePaperPdf(
            @Valid @RequestPart("metadata") PrivateExamPdfConfirmRequest metadata,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamPdfImportService.confirm(metadata, file, userDetails.getUserId()));
    }

    @PostMapping(value = "/private-papers/drafts/pdf", consumes = "multipart/form-data")
    public R<PrivateExamDraftVO> createPrivatePaperPdfDraft(
            @Valid @RequestPart("metadata") PrivateExamPdfDraftCreateRequest metadata,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamPdfImportService.createDraft(metadata, file, userDetails.getUserId()));
    }

    @PostMapping(value = "/private-papers/import/docx/preview", consumes = "multipart/form-data")
    public R<PrivateExamImportPreviewVO> previewPrivatePaperDocx(
            @Valid @RequestPart("metadata") PrivateExamDocxRequest metadata,
            @RequestPart("file") MultipartFile file) {
        return R.ok(privateExamDocxImportService.preview(metadata, file));
    }

    @PostMapping(value = "/private-papers/import/docx/confirm", consumes = "multipart/form-data")
    public R<ExamPaperVO> confirmPrivatePaperDocx(
            @Valid @RequestPart("metadata") PrivateExamDocxConfirmRequest metadata,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDocxImportService.confirm(metadata, file, userDetails.getUserId()));
    }

    @PostMapping(value = "/private-papers/drafts/docx", consumes = "multipart/form-data")
    public R<PrivateExamDraftVO> createPrivatePaperDocxDraft(
            @Valid @RequestPart("metadata") PrivateExamDocxDraftCreateRequest metadata,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDocxImportService.createDraft(metadata, file, userDetails.getUserId()));
    }

    @PostMapping("/private-papers/import/confirm")
    public R<ExamPaperVO> confirmPrivatePaper(
            @Valid @RequestBody PrivateExamImportConfirmRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamImportService.confirm(request, userDetails.getUserId()));
    }

    @PostMapping("/private-papers/drafts")
    public R<PrivateExamDraftVO> createPrivatePaperDraft(
            @Valid @RequestBody PrivateExamDraftCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDraftService.create(request, userDetails.getUserId()));
    }

    @GetMapping("/private-papers/drafts/{draftId}")
    public R<PrivateExamDraftVO> getPrivatePaperDraft(
            @PathVariable Long draftId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDraftService.get(draftId, userDetails.getUserId()));
    }

    @GetMapping("/private-papers/drafts")
    public R<List<PrivateExamDraftVO>> listPrivatePaperDrafts(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDraftService.listActive(userDetails.getUserId()));
    }

    @PostMapping("/private-papers/drafts/{draftId}/questions/{questionId}/ai-answer")
    public R<PrivateExamDraftVO> generatePrivatePaperDraftAnswer(
            @PathVariable Long draftId,
            @PathVariable Long questionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDraftService.generateAnswer(draftId, questionId, userDetails.getUserId()));
    }

    @PutMapping("/private-papers/drafts/{draftId}/questions/{questionId}/review")
    public R<PrivateExamDraftVO> reviewPrivatePaperDraftQuestion(
            @PathVariable Long draftId,
            @PathVariable Long questionId,
            @Valid @RequestBody PrivateExamDraftReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDraftService.reviewQuestion(draftId, questionId, request, userDetails.getUserId()));
    }

    @PostMapping("/private-papers/drafts/{draftId}/confirm")
    public R<ExamPaperVO> confirmPrivatePaperDraft(
            @PathVariable Long draftId,
            @Valid @RequestBody PrivateExamDraftConfirmRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDraftService.confirm(draftId, request, userDetails.getUserId()));
    }

    @DeleteMapping("/private-papers/drafts/{draftId}")
    public R<Void> deletePrivatePaperDraft(
            @PathVariable Long draftId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        privateExamContentLifecycleService.deleteDraft(draftId, userDetails.getUserId());
        return R.ok();
    }

    @DeleteMapping("/private-papers/{paperId}")
    public R<Void> deletePrivatePaper(
            @PathVariable Long paperId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        privateExamContentLifecycleService.deletePaper(paperId, userDetails.getUserId());
        return R.ok();
    }

    @GetMapping("/private-papers/{paperId}/source")
    public R<PrivateExamSourceVO> getPrivatePaperSource(
            @PathVariable Long paperId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamImportService.getSource(paperId, userDetails.getUserId()));
    }

    @GetMapping("/private-papers/{paperId}/source/file")
    public ResponseEntity<byte[]> downloadPrivatePaperSourceFile(
            @PathVariable Long paperId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return sourceFileResponse(privateExamSourceFileService.getForPaper(paperId, userDetails.getUserId()));
    }

    @GetMapping("/private-papers/drafts/{draftId}/source/file")
    public ResponseEntity<byte[]> downloadPrivatePaperDraftSourceFile(
            @PathVariable Long draftId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return sourceFileResponse(privateExamSourceFileService.getForDraft(draftId, userDetails.getUserId()));
    }

    private ResponseEntity<byte[]> sourceFileResponse(PrivateExamSourceFile file) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(file.content());
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
