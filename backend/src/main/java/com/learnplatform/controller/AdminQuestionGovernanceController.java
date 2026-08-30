package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.QuestionCorrectionProcessRequest;
import com.learnplatform.dto.QuestionCorrectionReportVO;
import com.learnplatform.dto.QuestionReReviewRequest;
import com.learnplatform.dto.QuestionReviewRecordVO;
import com.learnplatform.dto.QuestionReviewSuggestionVO;
import com.learnplatform.dto.QuestionSourceStatsVO;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.dto.QuestionVersionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.QuestionCorrectionReportService;
import com.learnplatform.service.QuestionReviewSuggestionService;
import com.learnplatform.service.QuestionService;
import com.learnplatform.service.QuestionSourceService;
import com.learnplatform.service.QuestionVersionService;
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

import java.util.List;

/**
 * 管理端题目来源、纠错、复审与版本治理入口。
 */
@Tag(name = "管理端-题目治理", description = "管理端题目来源、纠错、复审与版本接口")
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionGovernanceController {

    private final QuestionService questionService;
    private final QuestionSourceService questionSourceService;
    private final QuestionReviewSuggestionService questionReviewSuggestionService;
    private final QuestionCorrectionReportService correctionReportService;
    private final QuestionVersionService questionVersionService;

    public AdminQuestionGovernanceController(
            QuestionService questionService,
            QuestionSourceService questionSourceService,
            QuestionReviewSuggestionService questionReviewSuggestionService,
            QuestionCorrectionReportService correctionReportService,
            QuestionVersionService questionVersionService) {
        this.questionService = questionService;
        this.questionSourceService = questionSourceService;
        this.questionReviewSuggestionService = questionReviewSuggestionService;
        this.correctionReportService = correctionReportService;
        this.questionVersionService = questionVersionService;
    }

    @Operation(summary = "题目版本记录", description = "查看指定题目的创建、修改、删除与复审变更快照")
    @GetMapping("/{id}/versions")
    public R<List<QuestionVersionVO>> getQuestionVersions(@PathVariable Long id) {
        questionService.getQuestionById(id);
        return R.ok(questionVersionService.getQuestionVersions(id));
    }

    @Operation(summary = "题目来源统计", description = "获取各来源类型的题目数量统计")
    @GetMapping("/source-stats")
    public R<List<QuestionSourceStatsVO>> getSourceStats() {
        return R.ok(questionSourceService.getSourceStats());
    }

    @Operation(summary = "来源类型列表", description = "获取所有来源类型标识")
    @GetMapping("/source-types")
    public R<List<String>> getSourceTypes() {
        return R.ok(questionSourceService.getSourceTypes());
    }

    @Operation(summary = "题目纠错反馈", description = "分页查看用户提交的正式题目纠错反馈")
    @GetMapping("/correction-reports")
    public R<Page<QuestionCorrectionReportVO>> listCorrectionReports(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long questionId) {
        return R.ok(correctionReportService.getAdminReports(pageNum, pageSize, status, questionId));
    }

    @Operation(summary = "处理题目纠错反馈", description = "管理员标记纠错反馈为已处理、驳回或重新打开")
    @PostMapping("/correction-reports/{reportId}/process")
    public R<QuestionCorrectionReportVO> processCorrectionReport(
            @PathVariable Long reportId,
            @Valid @RequestBody QuestionCorrectionProcessRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(correctionReportService.processReport(reportId, request, userDetails.getUserId()));
    }

    @Operation(summary = "待复审题目", description = "获取超过复审周期的题目列表")
    @GetMapping("/review-overdue")
    public R<Page<QuestionVO>> getReviewOverdue(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(questionSourceService.getOverdueReviews(pageNum, pageSize));
    }

    @Operation(summary = "复审记录", description = "获取指定题目的所有复审记录")
    @GetMapping("/{id}/review-records")
    public R<List<QuestionReviewRecordVO>> getReviewRecords(@PathVariable Long id) {
        questionService.getQuestionById(id);
        return R.ok(questionSourceService.getReviewRecords(id));
    }

    @Operation(summary = "AI 复审建议", description = "对正式题目生成缓存化 AI 复审建议")
    @GetMapping("/{id}/review-suggestion")
    public R<QuestionReviewSuggestionVO> getReviewSuggestion(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        questionService.getQuestionById(id);
        return R.ok(questionReviewSuggestionService.generateSuggestion(id, userDetails.getUserId()));
    }

    @Operation(summary = "执行复审", description = "对题目执行复审（通过/修订/标记废弃）")
    @PostMapping("/{id}/re-review")
    public R<QuestionReviewRecordVO> performReReview(
            @PathVariable Long id,
            @Valid @RequestBody QuestionReReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        questionService.getQuestionById(id);
        return R.ok(questionSourceService.performReReview(id, request, userDetails.getUserId()));
    }
}
