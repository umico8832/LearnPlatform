package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.QuestionImportResult;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.MarkdownQuestionParser;
import com.learnplatform.service.QuestionImportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 管理端题目导入、导出与模板下载入口。
 */
@Tag(name = "管理端-题目导入导出", description = "管理端题目文件交换接口")
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionImportExportController {

    private static final ClassPathResource MARKDOWN_TEMPLATE =
            new ClassPathResource("templates/question-import-template.md");

    private final QuestionImportExportService questionImportExportService;
    private final MarkdownQuestionParser markdownQuestionParser;

    public AdminQuestionImportExportController(
            QuestionImportExportService questionImportExportService,
            MarkdownQuestionParser markdownQuestionParser) {
        this.questionImportExportService = questionImportExportService;
        this.markdownQuestionParser = markdownQuestionParser;
    }

    @Operation(summary = "导出题目", description = "导出题目到 Excel 文件，支持按题型、课程、难度筛选")
    @GetMapping("/export")
    public void exportQuestions(
            HttpServletResponse response,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer difficulty) throws IOException {
        questionImportExportService.exportQuestions(response, questionType, courseId, difficulty);
    }

    @Operation(summary = "下载导入模板", description = "下载题目导入 Excel 模板")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        questionImportExportService.downloadTemplate(response);
    }

    @Operation(summary = "导入题目(Excel)", description = "从 Excel 文件批量导入题目")
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

    @Operation(summary = "导入题目(Markdown)", description = "从 Markdown 文件批量导入题目，支持结构化字段格式")
    @PostMapping("/import-markdown")
    public R<QuestionImportResult> importQuestionsFromMarkdown(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IOException {
        if (file.isEmpty()) {
            return R.businessError("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.endsWith(".md") && !originalName.endsWith(".markdown")) {
            return R.businessError("仅支持 .md 或 .markdown 文件");
        }
        QuestionImportResult result = markdownQuestionParser.importFromMarkdown(
                file.getInputStream(), userDetails.getUserId());
        return R.ok(result);
    }

    @Operation(summary = "下载 Markdown 导入模板", description = "下载题目导入 Markdown 模板文件")
    @GetMapping("/template-markdown")
    public void downloadMarkdownTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("text/markdown; charset=utf-8");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("题目导入模板", StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".md");
        try (InputStream input = MARKDOWN_TEMPLATE.getInputStream()) {
            input.transferTo(response.getOutputStream());
        }
    }
}
