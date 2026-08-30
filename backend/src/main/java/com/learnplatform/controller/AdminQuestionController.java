package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionDuplicateGroupVO;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.QuestionService;
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

import java.util.List;

/**
 * 管理端题目查询与维护入口。
 */
@Tag(name = "管理端-题目管理", description = "管理端题目查询与维护接口")
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionController {

    private final QuestionService questionService;

    public AdminQuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

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

    @Operation(summary = "疑似重复题目", description = "按课程和题型分桶检测题干精确重复或高相似题目")
    @GetMapping("/duplicates")
    public R<List<QuestionDuplicateGroupVO>> findDuplicateQuestions(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Integer minSimilarity,
            @RequestParam(required = false) Integer limit) {
        return R.ok(questionService.findDuplicateGroups(courseId, questionType, minSimilarity, limit));
    }

    @Operation(summary = "题目详情", description = "获取题目详情，包含选项和知识点关联")
    @GetMapping("/{id}")
    public R<QuestionVO> getQuestion(@PathVariable Long id) {
        return R.ok(questionService.getQuestionById(id));
    }

    @Operation(summary = "创建题目", description = "创建题目，包含选项和知识点关联")
    @PostMapping
    public R<QuestionVO> createQuestion(
            @Valid @RequestBody QuestionCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(questionService.createQuestion(request, userDetails.getUserId()));
    }

    @Operation(summary = "更新题目", description = "更新题目基本信息、选项和知识点关联")
    @PutMapping("/{id}")
    public R<QuestionVO> updateQuestion(
            @PathVariable Long id,
            @Valid @RequestBody QuestionCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(questionService.updateQuestion(id, request, userDetails.getUserId()));
    }

    @Operation(summary = "删除题目", description = "级联删除题目及其选项和知识点关联")
    @DeleteMapping("/{id}")
    public R<Void> deleteQuestion(@PathVariable Long id,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        questionService.deleteQuestion(id, userDetails.getUserId());
        return R.ok();
    }
}
