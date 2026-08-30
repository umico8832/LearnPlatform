package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.PrivateExamSourceStorageItemVO;
import com.learnplatform.dto.PrivateExamSourceVO;
import com.learnplatform.dto.PrivateExamStorageUsageVO;
import com.learnplatform.dto.exam.PrivateExamSourceFile;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.PrivateExamContentLifecycleService;
import com.learnplatform.service.PrivateExamImportService;
import com.learnplatform.service.PrivateExamSourceFileService;
import com.learnplatform.service.PrivateExamSourceStorageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 用户私有试卷来源、原文件、存储配额与删除入口。
 */
@Tag(name = "私有试卷来源", description = "用户私有试卷来源与原文件管理接口")
@RestController
@RequestMapping("/api/exam/private-papers")
public class PrivateExamSourceController {

    private final PrivateExamImportService privateExamImportService;
    private final PrivateExamContentLifecycleService privateExamContentLifecycleService;
    private final PrivateExamSourceFileService privateExamSourceFileService;
    private final PrivateExamSourceStorageService privateExamSourceStorageService;

    public PrivateExamSourceController(
            PrivateExamImportService privateExamImportService,
            PrivateExamContentLifecycleService privateExamContentLifecycleService,
            PrivateExamSourceFileService privateExamSourceFileService,
            PrivateExamSourceStorageService privateExamSourceStorageService) {
        this.privateExamImportService = privateExamImportService;
        this.privateExamContentLifecycleService = privateExamContentLifecycleService;
        this.privateExamSourceFileService = privateExamSourceFileService;
        this.privateExamSourceStorageService = privateExamSourceStorageService;
    }

    @DeleteMapping("/{paperId}")
    public R<Void> deletePrivatePaper(
            @PathVariable Long paperId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        privateExamContentLifecycleService.deletePaper(paperId, userDetails.getUserId());
        return R.ok();
    }

    @GetMapping("/{paperId}/source")
    public R<PrivateExamSourceVO> getPrivatePaperSource(
            @PathVariable Long paperId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamImportService.getSource(paperId, userDetails.getUserId()));
    }

    @GetMapping("/source-storage")
    public R<PrivateExamStorageUsageVO> getPrivatePaperSourceStorage(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamSourceStorageService.getUsage(userDetails.getUserId()));
    }

    @GetMapping("/source-storage/files")
    public R<Page<PrivateExamSourceStorageItemVO>> listPrivatePaperSourceStorageFiles(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamSourceStorageService.listFiles(userDetails.getUserId(), pageNum, pageSize));
    }

    @GetMapping("/{paperId}/source/file")
    public ResponseEntity<byte[]> downloadPrivatePaperSourceFile(
            @PathVariable Long paperId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return sourceFileResponse(privateExamSourceFileService.getForPaper(paperId, userDetails.getUserId()));
    }

    @GetMapping("/drafts/{draftId}/source/file")
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
}
