package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamDraftConfirmRequest;
import com.learnplatform.dto.PrivateExamDraftReviewRequest;
import com.learnplatform.dto.PrivateExamDraftVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.PrivateExamContentLifecycleService;
import com.learnplatform.service.PrivateExamDraftService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户私有试卷草稿复核与确认入口。
 */
@Tag(name = "私有试卷草稿", description = "用户私有试卷草稿复核与确认接口")
@RestController
@RequestMapping("/api/exam/private-papers/drafts")
public class PrivateExamDraftController {

    private final PrivateExamDraftService privateExamDraftService;
    private final PrivateExamContentLifecycleService privateExamContentLifecycleService;

    public PrivateExamDraftController(
            PrivateExamDraftService privateExamDraftService,
            PrivateExamContentLifecycleService privateExamContentLifecycleService) {
        this.privateExamDraftService = privateExamDraftService;
        this.privateExamContentLifecycleService = privateExamContentLifecycleService;
    }

    @GetMapping("/{draftId}")
    public R<PrivateExamDraftVO> getPrivatePaperDraft(
            @PathVariable Long draftId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDraftService.get(draftId, userDetails.getUserId()));
    }

    @GetMapping
    public R<List<PrivateExamDraftVO>> listPrivatePaperDrafts(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDraftService.listActive(userDetails.getUserId()));
    }

    @PostMapping("/{draftId}/questions/{questionId}/ai-answer")
    public R<PrivateExamDraftVO> generatePrivatePaperDraftAnswer(
            @PathVariable Long draftId,
            @PathVariable Long questionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDraftService.generateAnswer(draftId, questionId, userDetails.getUserId()));
    }

    @PutMapping("/{draftId}/questions/{questionId}/review")
    public R<PrivateExamDraftVO> reviewPrivatePaperDraftQuestion(
            @PathVariable Long draftId,
            @PathVariable Long questionId,
            @Valid @RequestBody PrivateExamDraftReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDraftService.reviewQuestion(draftId, questionId, request, userDetails.getUserId()));
    }

    @PostMapping("/{draftId}/confirm")
    public R<ExamPaperVO> confirmPrivatePaperDraft(
            @PathVariable Long draftId,
            @Valid @RequestBody PrivateExamDraftConfirmRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(privateExamDraftService.confirm(draftId, request, userDetails.getUserId()));
    }

    @DeleteMapping("/{draftId}")
    public R<Void> deletePrivatePaperDraft(
            @PathVariable Long draftId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        privateExamContentLifecycleService.deleteDraft(draftId, userDetails.getUserId());
        return R.ok();
    }
}
