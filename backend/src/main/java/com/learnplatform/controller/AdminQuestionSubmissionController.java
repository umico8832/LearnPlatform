package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.QuestionReviewRequest;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.QuestionSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 题目投稿管理控制器（管理端）
 */
@Tag(name = "题目投稿管理", description = "管理员审核、入库用户投稿的题目")
@RestController
@RequestMapping("/api/admin/submission")
public class AdminQuestionSubmissionController {

    private final QuestionSubmissionService submissionService;

    public AdminQuestionSubmissionController(QuestionSubmissionService submissionService) {
        this.submissionService = submissionService;
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

    @Operation(summary = "投稿入库", description = "将已通过审核的投稿导入题库")
    @PostMapping("/{id}/import")
    public R<QuestionSubmissionVO> importToQuestionBank(@PathVariable Long id) {
        Long adminId = getCurrentUserId();
        QuestionSubmissionVO vo = submissionService.importSubmission(id, adminId);
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