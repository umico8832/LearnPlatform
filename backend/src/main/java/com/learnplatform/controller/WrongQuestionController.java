package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.WrongQuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.WrongQuestionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 错题本控制器（用户端）
 */
@RestController
@RequestMapping("/api/wrong-questions")
public class WrongQuestionController {

    private final WrongQuestionService wrongQuestionService;

    public WrongQuestionController(WrongQuestionService wrongQuestionService) {
        this.wrongQuestionService = wrongQuestionService;
    }

    /**
     * 获取错题本列表（分页）
     */
    @GetMapping
    public R<Page<WrongQuestionVO>> getWrongQuestions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer masteryLevel) {
        Page<WrongQuestionVO> page = wrongQuestionService.getWrongQuestions(
                userDetails.getUserId(), pageNum, pageSize, courseId, masteryLevel);
        return R.ok(page);
    }

    /**
     * 获取错题本统计
     */
    @GetMapping("/stats")
    public R<Map<String, Object>> getStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> stats = wrongQuestionService.getWrongQuestionStats(userDetails.getUserId());
        return R.ok(stats);
    }

    /**
     * 更新掌握程度
     */
    @PutMapping("/{id}/mastery")
    public R<Void> updateMasteryLevel(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestParam Integer masteryLevel) {
        wrongQuestionService.updateMasteryLevel(id, userDetails.getUserId(), masteryLevel);
        return R.ok(null);
    }

    /**
     * 移出错题本
     */
    @DeleteMapping("/{id}")
    public R<Void> removeWrongQuestion(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        wrongQuestionService.removeWrongQuestion(id, userDetails.getUserId());
        return R.ok(null);
    }
}