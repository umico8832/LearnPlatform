package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.service.QuestionService;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端题目控制器
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /**
     * 分页查询题目（用户端，仅启用状态）
     */
    @GetMapping
    public R<Page<QuestionVO>> listQuestions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer difficulty) {
        return R.ok(questionService.getEnabledQuestionPage(pageNum, pageSize,
                questionType, courseId, difficulty));
    }

    /**
     * 获取题目详情
     */
    @GetMapping("/{id}")
    public R<QuestionVO> getQuestion(@PathVariable Long id) {
        return R.ok(questionService.getEnabledQuestionById(id));
    }
}
