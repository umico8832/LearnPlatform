package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.ExamPaperCreateRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiExamGenerationService;
import com.learnplatform.service.ExamPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端试卷控制器
 */
@Tag(name = "管理端-试卷管理", description = "管理端试卷CRUD接口")
@RestController
@RequestMapping("/api/admin/exam-papers")
public class AdminExamController {

    private final ExamPaperService examPaperService;
    private final AiExamGenerationService aiExamGenerationService;

    public AdminExamController(ExamPaperService examPaperService,
                               AiExamGenerationService aiExamGenerationService) {
        this.examPaperService = examPaperService;
        this.aiExamGenerationService = aiExamGenerationService;
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
}
