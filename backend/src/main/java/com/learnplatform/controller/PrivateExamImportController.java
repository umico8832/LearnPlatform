package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamDocxConfirmRequest;
import com.learnplatform.dto.PrivateExamDocxDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDocxRequest;
import com.learnplatform.dto.PrivateExamDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDraftVO;
import com.learnplatform.dto.PrivateExamImportConfirmRequest;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.dto.PrivateExamImportRequest;
import com.learnplatform.dto.PrivateExamPdfConfirmRequest;
import com.learnplatform.dto.PrivateExamPdfDraftCreateRequest;
import com.learnplatform.dto.PrivateExamPdfRequest;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.PrivateExamDocxImportService;
import com.learnplatform.service.PrivateExamDraftService;
import com.learnplatform.service.PrivateExamImportService;
import com.learnplatform.service.PrivateExamPdfImportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户私有试卷文本、PDF 与 DOCX 导入入口。
 */
@Tag(name = "私有试卷导入", description = "用户私有试卷预览、确认与草稿创建接口")
@RestController
@RequestMapping("/api/exam/private-papers")
public class PrivateExamImportController {

    private final PrivateExamImportService privateExamImportService;
    private final PrivateExamDraftService privateExamDraftService;
    private final PrivateExamPdfImportService privateExamPdfImportService;
    private final PrivateExamDocxImportService privateExamDocxImportService;

    public PrivateExamImportController(
            PrivateExamImportService privateExamImportService,
            PrivateExamDraftService privateExamDraftService,
            PrivateExamPdfImportService privateExamPdfImportService,
            PrivateExamDocxImportService privateExamDocxImportService) {
        this.privateExamImportService = privateExamImportService;
        this.privateExamDraftService = privateExamDraftService;
        this.privateExamPdfImportService = privateExamPdfImportService;
        this.privateExamDocxImportService = privateExamDocxImportService;
    }

    @PostMapping("/import/preview")
    public R<PrivateExamImportPreviewVO> previewPrivatePaper(
            @Valid @RequestBody PrivateExamImportRequest request) {
        return R.ok(privateExamImportService.preview(request));
    }

    @PostMapping(value = "/import/pdf/preview", consumes = "multipart/form-data")
    public R<PrivateExamImportPreviewVO> previewPrivatePaperPdf(
            @Valid @RequestPart("metadata") PrivateExamPdfRequest metadata,
            @RequestPart("file") MultipartFile file) {
        return R.ok(privateExamPdfImportService.preview(metadata, file));
    }

    @PostMapping(value = "/import/pdf/confirm", consumes = "multipart/form-data")
    public R<ExamPaperVO> confirmPrivatePaperPdf(
            @Valid @RequestPart("metadata") PrivateExamPdfConfirmRequest metadata,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamPdfImportService.confirm(metadata, file, userDetails.getUserId()));
    }

    @PostMapping(value = "/drafts/pdf", consumes = "multipart/form-data")
    public R<PrivateExamDraftVO> createPrivatePaperPdfDraft(
            @Valid @RequestPart("metadata") PrivateExamPdfDraftCreateRequest metadata,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamPdfImportService.createDraft(metadata, file, userDetails.getUserId()));
    }

    @PostMapping(value = "/import/docx/preview", consumes = "multipart/form-data")
    public R<PrivateExamImportPreviewVO> previewPrivatePaperDocx(
            @Valid @RequestPart("metadata") PrivateExamDocxRequest metadata,
            @RequestPart("file") MultipartFile file) {
        return R.ok(privateExamDocxImportService.preview(metadata, file));
    }

    @PostMapping(value = "/import/docx/confirm", consumes = "multipart/form-data")
    public R<ExamPaperVO> confirmPrivatePaperDocx(
            @Valid @RequestPart("metadata") PrivateExamDocxConfirmRequest metadata,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDocxImportService.confirm(metadata, file, userDetails.getUserId()));
    }

    @PostMapping(value = "/drafts/docx", consumes = "multipart/form-data")
    public R<PrivateExamDraftVO> createPrivatePaperDocxDraft(
            @Valid @RequestPart("metadata") PrivateExamDocxDraftCreateRequest metadata,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDocxImportService.createDraft(metadata, file, userDetails.getUserId()));
    }

    @PostMapping("/import/confirm")
    public R<ExamPaperVO> confirmPrivatePaper(
            @Valid @RequestBody PrivateExamImportConfirmRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamImportService.confirm(request, userDetails.getUserId()));
    }

    @PostMapping("/drafts")
    public R<PrivateExamDraftVO> createPrivatePaperDraft(
            @Valid @RequestBody PrivateExamDraftCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDraftService.create(request, userDetails.getUserId()));
    }
}
