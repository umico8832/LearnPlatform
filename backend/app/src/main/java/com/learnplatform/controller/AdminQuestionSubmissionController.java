package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.QuestionReviewRequest;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.dto.SubmissionKPTaggingVO;
import com.learnplatform.dto.SubmissionDifficultyVO;
import com.learnplatform.dto.SubmissionQualityCheckVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.QuestionSubmissionImportService;
import com.learnplatform.service.QuestionSubmissionService;
import com.learnplatform.service.SubmissionAiQualityService;
import com.learnplatform.service.SubmissionDifficultyAssessmentService;
import com.learnplatform.service.SubmissionKPTaggingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目投稿管理控制器（管理端）
 */
@Tag(name = "题目投稿管理", description = "管理员审核、入库用户投稿的题目")
@RestController
@RequestMapping("/api/admin/submission")
public class AdminQuestionSubmissionController {

    private final QuestionSubmissionService submissionService;
    private final QuestionSubmissionImportService submissionImportService;
    private final SubmissionAiQualityService qualityService;
    private final SubmissionKPTaggingService kpTaggingService;
    private final SubmissionDifficultyAssessmentService difficultyAssessmentService;

    public AdminQuestionSubmissionController(QuestionSubmissionService submissionService,
                                              QuestionSubmissionImportService submissionImportService,
                                              SubmissionAiQualityService qualityService,
                                              SubmissionKPTaggingService kpTaggingService,
                                              SubmissionDifficultyAssessmentService difficultyAssessmentService) {
        this.submissionService = submissionService;
        this.submissionImportService = submissionImportService;
        this.qualityService = qualityService;
        this.kpTaggingService = kpTaggingService;
        this.difficultyAssessmentService = difficultyAssessmentService;
    }

    @Operation(summary = "投稿列表", description = "查看所有投稿，可按状态、课程、关键词筛选")
    @GetMapping
    public R<Page<QuestionSubmissionVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword) {
        Page<QuestionSubmissionVO> page = submissionService.getAllSubmissions(
                pageNum, pageSize, status, courseId, keyword);
        return R.ok(page);
    }

    @Operation(summary = "投稿详情", description = "查看指定投稿的详细信息")
    @GetMapping("/{id}")
    public R<QuestionSubmissionVO> detail(@PathVariable Long id) {
        QuestionSubmissionVO vo = submissionService.getSubmissionById(id);
        return R.ok(vo);
    }

    @Operation(summary = "审核投稿", description = "通过或拒绝用户投稿的题目")
    @PostMapping("/{id}/review")
    public R<QuestionSubmissionVO> review(@PathVariable Long id,
                                           @Valid @RequestBody QuestionReviewRequest request) {
        Long adminId = getCurrentUserId();
        QuestionSubmissionVO vo = submissionService.reviewSubmission(id, request, adminId);
        return R.ok(vo);
    }

    @Operation(summary = "AI 质检", description = "对投稿进行 AI 质量检查，返回多维度检查结果（不改变投稿状态）")
    @PostMapping("/{id}/quality-check")
    public R<SubmissionQualityCheckVO> qualityCheck(@PathVariable Long id) {
        Long adminId = getCurrentUserId();
        SubmissionQualityCheckVO vo = qualityService.checkQuality(id, adminId);
        return R.ok(vo);
    }

    @Operation(summary = "AI 知识点标注", description = "AI 分析投稿内容，推荐最相关的知识点（不改变投稿数据）")
    @PostMapping("/{id}/kp-tagging")
    public R<SubmissionKPTaggingVO> kpTagging(@PathVariable Long id) {
        Long adminId = getCurrentUserId();
        SubmissionKPTaggingVO vo = kpTaggingService.tagKnowledgePoints(id, adminId);
        return R.ok(vo);
    }

    @Operation(summary = "应用知识点标注", description = "将 AI 推荐的知识点 ID 应用到投稿的 knowledgePointIds 字段")
    @PostMapping("/{id}/apply-kp")
    public R<QuestionSubmissionVO> applyKnowledgePoints(@PathVariable Long id,
                                                         @RequestParam String knowledgePointIds) {
        QuestionSubmissionVO vo = submissionService.updateKnowledgePointIds(id, knowledgePointIds);
        return R.ok(vo);
    }

    @Operation(summary = "AI 难度评估", description = "AI 评估题目难度，与投稿者标注对比（不改变投稿数据）")
    @PostMapping("/{id}/difficulty-assessment")
    public R<SubmissionDifficultyVO> assessDifficulty(@PathVariable Long id) {
        Long adminId = getCurrentUserId();
        SubmissionDifficultyVO vo = difficultyAssessmentService.assessDifficulty(id, adminId);
        return R.ok(vo);
    }

    @Operation(summary = "AI 生成审核意见", description = "基于 AI 质检结果自动生成审核意见建议文本，供管理员一键填充")
    @PostMapping("/{id}/generate-review-comment")
    public R<String> generateReviewComment(@PathVariable Long id) {
        Long adminId = getCurrentUserId();
        String comment = qualityService.generateReviewComment(id, adminId);
        return R.ok(comment);
    }

    @Operation(summary = "投稿入库", description = "将已通过审核的投稿导入题库")
    @PostMapping("/{id}/import")
    public R<QuestionSubmissionVO> importToQuestionBank(@PathVariable Long id) {
        Long adminId = getCurrentUserId();
        QuestionSubmissionVO vo = submissionImportService.importSubmission(id, adminId);
        return R.ok(vo);
    }

    @Operation(summary = "投稿统计", description = "各状态投稿数量")
    @GetMapping("/stats")
    public R<SubmissionStatsVO> stats() {
        long pending = submissionService.countByStatus(0);
        long approved = submissionService.countByStatus(1);
        long rejected = submissionService.countByStatus(2);
        long imported = submissionService.countByStatus(3);
        SubmissionStatsVO stats = new SubmissionStatsVO(pending, approved, rejected, imported);
        return R.ok(stats);
    }

    private Long getCurrentUserId() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userDetails.getUserId();
    }

    /**
     * 投稿统计 VO
     */
    public static class SubmissionStatsVO {
        private long pending;
        private long approved;
        private long rejected;
        private long imported;

        public SubmissionStatsVO(long pending, long approved, long rejected, long imported) {
            this.pending = pending;
            this.approved = approved;
            this.rejected = rejected;
            this.imported = imported;
        }

        public long getPending() { return pending; }
        public void setPending(long pending) { this.pending = pending; }
        public long getApproved() { return approved; }
        public void setApproved(long approved) { this.approved = approved; }
        public long getRejected() { return rejected; }
        public void setRejected(long rejected) { this.rejected = rejected; }
        public long getImported() { return imported; }
        public void setImported(long imported) { this.imported = imported; }
    }
}
