package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.QuestionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端题目控制器
 */
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
    @GetMapping("/{id}")
    public R<QuestionVO> getQuestion(@PathVariable Long id) {
        return R.ok(questionService.getQuestionById(id));
    }

    /**
     * 创建题目
     */
    @PostMapping
    public R<QuestionVO> createQuestion(
            @RequestBody QuestionCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return R.ok(questionService.createQuestion(request, userDetails.getUserId()));
    }

    /**
     * 更新题目
     */
    @PutMapping("/{id}")
    public R<QuestionVO> updateQuestion(
            @PathVariable Long id,
            @RequestBody QuestionCreateRequest request) {
        return R.ok(questionService.updateQuestion(id, request));
    }

    /**
     * 删除题目
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteQuestion(@PathVariable Long id) {
        questionService.deleteQuestion(id);
        return R.ok();
    }
}