package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端题目控制器
 */
@Tag(name = "管理端-题目管理", description = "管理端题目CRUD接口")
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionController {

    private final QuestionService questionService;

    public AdminQuestionController(QuestionService questionService) {
        this.questionService = questionService;
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
}