package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.ExamPaperCreateRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.SubjectiveAnswerReviewVO;
import com.learnplatform.dto.SubjectiveGradingRequest;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiExamGenerationService;
import com.learnplatform.service.ExamPaperService;
import com.learnplatform.service.SubjectiveExamGradingService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端试卷控制器
 */
@Tag(name = "管理端-试卷管理", description = "管理端试卷CRUD接口")
@RestController
@RequestMapping("/api/admin/exam-papers")
public class AdminExamController {

    private final ExamPaperService examPaperService;
    private final AiExamGenerationService aiExamGenerationService;
    private final SubjectiveExamGradingService subjectiveExamGradingService;

    public AdminExamController(ExamPaperService examPaperService,
                               AiExamGenerationService aiExamGenerationService,
                               SubjectiveExamGradingService subjectiveExamGradingService) {
        this.examPaperService = examPaperService;
        this.aiExamGenerationService = aiExamGenerationService;
        this.subjectiveExamGradingService = subjectiveExamGradingService;
    }

    @Operation(summary = "试卷列表", description = "分页查询试卷列表")
    @GetMapping
    public R<Page<ExamPaperVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer status) {
        return R.ok(examPaperService.getExamPaperPage(pageNum, pageSize, courseId, status));
    }

    @Operation(summary = "试卷详情", description = "获取试卷详情")
    @GetMapping("/{id}")
    public R<ExamPaperVO> detail(@PathVariable Long id) {
        return R.ok(examPaperService.getExamPaperById(id));
    }

    @Operation(summary = "创建试卷", description = "创建试卷，可包含题目列表")
    @PostMapping
    public R<ExamPaperVO> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ExamPaperCreateRequest request) {
        return R.ok(examPaperService.createExamPaper(request, userDetails.getUserId()));
    }

    @Operation(summary = "更新试卷", description = "更新试卷信息")
    @PutMapping("/{id}")
    public R<ExamPaperVO> update(@PathVariable Long id, @Valid @RequestBody ExamPaperCreateRequest request) {
        return R.ok(examPaperService.updateExamPaper(id, request));
    }

    @Operation(summary = "删除试卷", description = "删除试卷")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        examPaperService.deleteExamPaper(id);
        return R.ok(null);
    }

    @Operation(summary = "发布试卷", description = "将试卷状态改为已发布")
    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        examPaperService.publishExamPaper(id);
        return R.ok(null);
    }

    @Operation(summary = "智能组卷预览", description = "根据知识点覆盖和难度分布智能推荐题目组合")
    @PostMapping("/smart-preview")
    public R<AiExamGenerationService.SmartExamPreview> smartPreview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AiExamGenerationService.SmartExamRequest request) {
        return R.ok(aiExamGenerationService.preview(request, userDetails.getUserId()));
    }

    @Operation(summary = "确认智能组卷", description = "根据预览结果创建智能试卷")
    @PostMapping("/smart-create")
    public R<ExamPaperVO> smartCreate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AiExamGenerationService.SmartExamPreview preview) {
        return R.ok(aiExamGenerationService.createSmartExam(preview, userDetails.getUserId()));
    }

    @Operation(summary = "待人工批阅的主观题答案")
    @GetMapping("/subjective-reviews/pending")
    public R<java.util.List<SubjectiveAnswerReviewVO>> pendingSubjectiveReviews() {
        return R.ok(subjectiveExamGradingService.listPending());
    }

    @Operation(summary = "按评分点批阅主观题答案")
    @PostMapping("/subjective-reviews/{answerId}")
    public R<SubjectiveAnswerReviewVO> gradeSubjectiveAnswer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long answerId,
            @Valid @RequestBody SubjectiveGradingRequest request) {
        return R.ok(subjectiveExamGradingService.grade(answerId, request, userDetails.getUserId()));
    }
}
