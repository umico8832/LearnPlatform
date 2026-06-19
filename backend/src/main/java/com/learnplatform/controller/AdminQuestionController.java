package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.*;
import com.learnplatform.entity.Question;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.MarkdownQuestionParser;
import com.learnplatform.service.QuestionImportExportService;
import com.learnplatform.service.QuestionService;
import com.learnplatform.service.QuestionSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 管理端题目控制器
 */
@Tag(name = "管理端-题目管理", description = "管理端题目CRUD接口")
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionController {

    private final QuestionService questionService;
    private final QuestionImportExportService questionImportExportService;
    private final MarkdownQuestionParser markdownQuestionParser;
    private final QuestionSourceService questionSourceService;

    public AdminQuestionController(QuestionService questionService,
                                    QuestionImportExportService questionImportExportService,
                                    MarkdownQuestionParser markdownQuestionParser,
                                    QuestionSourceService questionSourceService) {
        this.questionService = questionService;
        this.questionImportExportService = questionImportExportService;
        this.markdownQuestionParser = markdownQuestionParser;
        this.questionSourceService = questionSourceService;
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
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String sourceType) {
        return R.ok(questionService.getQuestionPage(pageNum, pageSize, keyword,
                questionType, courseId, difficulty, status, sourceType));
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
     * 题目来源统计
     */
    @Operation(summary = "题目来源统计", description = "获取各来源类型的题目数量统计")
    @GetMapping("/source-stats")
    public R<List<QuestionSourceStatsVO>> getSourceStats() {
        return R.ok(questionSourceService.getSourceStats());
    }

    /**
     * 来源类型列表（用于前端筛选下拉）
     */
    @Operation(summary = "来源类型列表", description = "获取所有来源类型标识")
    @GetMapping("/source-types")
    public R<List<String>> getSourceTypes() {
        return R.ok(questionSourceService.getSourceTypes());
    }

    /**
     * 待复审题目列表
     */
    @Operation(summary = "待复审题目", description = "获取超过复审周期的题目列表")
    @GetMapping("/review-overdue")
    public R<Page<Question>> getReviewOverdue(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(questionSourceService.getOverdueReviews(pageNum, pageSize));
    }

    /**
     * 获取题目的复审记录
     */
    @Operation(summary = "复审记录", description = "获取指定题目的所有复审记录")
    @GetMapping("/{id}/review-records")
    public R<List<QuestionReviewRecordVO>> getReviewRecords(@PathVariable Long id) {
        return R.ok(questionSourceService.getReviewRecords(id));
    }

    /**
     * 执行复审
     */
    @Operation(summary = "执行复审", description = "对题目执行复审（通过/修订/标记废弃）")
    @PostMapping("/{id}/re-review")
    public R<QuestionReviewRecordVO> performReReview(
            @PathVariable Long id,
            @Valid @RequestBody QuestionReReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(questionSourceService.performReReview(id, request, userDetails.getUserId()));
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

    /**
     * 从 Markdown 导入题目
     */
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

    /**
     * 下载 Markdown 导入模板
     */
    @Operation(summary = "下载 Markdown 导入模板", description = "下载题目导入 Markdown 模板文件")
    @GetMapping("/template-markdown")
    public void downloadMarkdownTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("text/markdown; charset=utf-8");
        response.setCharacterEncoding("utf-8");
        String fileName = java.net.URLEncoder.encode("题目导入模板", java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".md");

        String template = """
                # 题目导入模板

                以下为示例格式，请按此格式编写题目，用 `---` 分隔每道题。

                ## 1. 单选题

                **题干**: 以下哪个是 Java 的基本数据类型？

                **选项**:
                - A. int
                - B. String
                - C. ArrayList
                - D. HashMap

                **答案**: A

                **解析**: int 是 Java 的 8 种基本数据类型之一，String、ArrayList、HashMap 是引用类型。

                **课程**: Java 基础
                **难度**: 2
                **知识点**: Java 语言基础, 面向对象
                **标签**: 基础
                **分值**: 2

                ---

                ## 2. 判断题

                **题干**: Java 是一种编译型语言。

                **选项**:
                - 对
                - 错

                **答案**: 错

                **解析**: Java 既是编译型语言（编译为字节码），也是解释型语言（JVM 解释执行字节码）。

                **课程**: Java 基础
                **难度**: 1

                ---

                ## 3. 多选题

                **题干**: 以下哪些是 Java 集合框架的接口？

                **选项**:
                - A. List
                - B. Set
                - C. Array
                - D. Map

                **答案**: A,B,D

                **解析**: Array 是数组，不是集合框架的接口。List、Set、Map 是集合框架三大核心接口。

                **课程**: Java 基础
                **难度**: 3
                **知识点**: 集合框架
                **标签**: 基础

                ---

                ## 4. 填空题

                **题干**: Java 中用于创建对象的关键字是 ______。

                **答案**: new

                **解析**: new 关键字用于实例化对象。

                **课程**: Java 基础
                **难度**: 1

                ---

                ## 5. 简答题

                **题干**: 请简述 Java 中 == 和 equals() 的区别。

                **答案**: == 比较的是引用（内存地址），equals() 比较的是内容（需重写）。Object 默认 equals() 等同于 ==。

                **课程**: Java 基础
                **难度**: 2

                ---

                ## 格式说明

                ### 必填字段
                - **题干**: 题目内容
                - **答案**: 正确答案（单选用字母或文字，多选用 A,B,C 格式）
                - **课程**: 必须与系统中已有课程名称完全一致

                ### 选填字段
                - **选项**: 选择题选项，以 `- A. ` 格式书写；判断题可省略（自动生成对/错）
                - **解析**: 答案解析
                - **难度**: 1-5，默认 3
                - **知识点**: 逗号分隔，不存在的知识点自动跳过
                - **标签**: 逗号分隔
                - **分值**: 默认 1
                - **题型**: 可通过标题识别（如 `## 1. 单选题`），也可用 `**题型**: 单选` 显式指定

                ### 题型识别规则
                1. 标题含"单选/多选/判断/填空/简答"→ 直接识别
                2. 无题型标题时自动推断：
                   - 选项为"对/错"或"正确/错误" → 判断题
                   - 答案含逗号/顿号 → 多选题
                   - 有选项 → 单选题
                   - 无选项 → 简答题
                """;
        response.getOutputStream().write(template.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
