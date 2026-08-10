package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.WrongQuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.WrongQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 错题本控制器（用户端）
 */
@Tag(name = "错题本", description = "用户端错题本管理相关接口")
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
    @Operation(summary = "错题列表", description = "分页获取当前用户的错题本列表")
    @GetMapping
    public R<Page<WrongQuestionVO>> getWrongQuestions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long questionId,
            @RequestParam(required = false) Integer masteryLevel) {
        Page<WrongQuestionVO> page = wrongQuestionService.getWrongQuestions(
                userDetails.getUserId(), pageNum, pageSize, courseId, questionId, masteryLevel);
        return R.ok(page);
    }


    /**
     * 获取错题本统计
     */
    @Operation(summary = "错题统计", description = "获取当前用户的错题统计数据")
    @GetMapping("/stats")
    public R<Map<String, Object>> getStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> stats = wrongQuestionService.getWrongQuestionStats(userDetails.getUserId());
        return R.ok(stats);
    }


    /**
     * 更新掌握程度
     */
    @Operation(summary = "更新掌握程度", description = "更新错题的掌握程度（未掌握/部分掌握/已掌握）")
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
    @Operation(summary = "移出错题本", description = "将错题从错题本中移出")
    @DeleteMapping("/{id}")
    public R<Void> removeWrongQuestion(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        wrongQuestionService.removeWrongQuestion(id, userDetails.getUserId());
        return R.ok(null);
    }
}
