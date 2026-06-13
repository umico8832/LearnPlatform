package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionImportResult;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.QuestionImportExportService;
import com.learnplatform.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 管理端题目控制器
 */
@Tag(name = "管理端-题目管理", description = "管理端题目CRUD接口")
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionController {

    private final QuestionService questionService;
    private final QuestionImportExportService questionImportExportService;

    public AdminQuestionController(QuestionService questionService,
                                    QuestionImportExportService questionImportExportService) {
        this.questionService = questionService;
        this.questionImportExportService = questionImportExportService;
    }


    /**
     * 分页查询题目（管理端）
     */
    @Operation(summary = "题目列表", description = "分页查询题目列表，支持多维度筛选")
    @GetMapping
    public R<Page<QuestionVO>> listQuestions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Integer status) {
        return R.ok(questionService.getQuestionPage(pageNum, pageSize, keyword,
                questionType, courseId, difficulty, status));
    }


    /**
     * 获取题目详情
     */
    @Operation(summary = "题目详情", description = "获取题目详情，包含选项和知识点关联")
    @GetMapping("/{id}")
    public R<QuestionVO> getQuestion(@PathVariable Long id) {
        return R.ok(questionService.getQuestionById(id));
    }


    /**
     * 创建题目
     */
    @Operation(summary = "创建题目", description = "创建题目，包含选项和知识点关联")
    @PostMapping
    public R<QuestionVO> createQuestion(
            @Valid @RequestBody QuestionCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(questionService.createQuestion(request, userDetails.getUserId()));
    }


    /**
     * 更新题目
     */
    @Operation(summary = "更新题目", description = "更新题目基本信息、选项和知识点关联")
    @PutMapping("/{id}")
    public R<QuestionVO> updateQuestion(
            @PathVariable Long id,
            @Valid @RequestBody QuestionCreateRequest request) {
        return R.ok(questionService.updateQuestion(id, request));
    }


    /**
     * 删除题目
     */
    @Operation(summary = "删除题目", description = "级联删除题目及其选项和知识点关联")
    @DeleteMapping("/{id}")
    public R<Void> deleteQuestion(@PathVariable Long id) {
        questionService.deleteQuestion(id);
        return R.ok();
    }

    /**
     * 导出题目到 Excel
     */
    @Operation(summary = "导出题目", description = "导出题目到 Excel 文件，支持按题型、课程、难度筛选")
    @GetMapping("/export")
    public void exportQuestions(
            HttpServletResponse response,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer difficulty) throws IOException {
        questionImportExportService.exportQuestions(response, questionType, courseId, difficulty);
    }

    /**
     * 下载导入模板
     */
    @Operation(summary = "下载导入模板", description = "下载题目导入 Excel 模板")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        questionImportExportService.downloadTemplate(response);
    }

    /**
     * 从 Excel 导入题目
     */
    @Operation(summary = "导入题目", description = "从 Excel 文件批量导入题目")
    @PostMapping("/import")
    public R<QuestionImportResult> importQuestions(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IOException {
        if (file.isEmpty()) {
            return R.businessError("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.endsWith(".xlsx") && !originalName.endsWith(".xls")) {
            return R.businessError("仅支持 .xlsx 或 .xls 文件");
        }
        QuestionImportResult result = questionImportExportService.importQuestions(
                file.getInputStream(), userDetails.getUserId());
        return R.ok(result);
    }
}
