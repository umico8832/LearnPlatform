package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.*;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.PracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 刷题控制器（用户端）
 */
@Tag(name = "刷题练习", description = "用户端刷题练习相关接口")
@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    /**
     * 获取练习题目
     * 支持按课程、知识点、题型、难度筛选，随机返回指定数量
     */
    @Operation(summary = "获取练习题目", description = "按条件随机抽取题目用于练习")
    @GetMapping("/questions")
    public R<List<QuestionVO>> getPracticeQuestions(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long knowledgePointId,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Integer count) {
        List<QuestionVO> questions = practiceService.getPracticeQuestions(
                courseId, knowledgePointId, questionType, difficulty, count);
        return R.ok(questions);
    }

    /**
     * 提交答案
     */
    @Operation(summary = "提交答案", description = "提交练习答案，系统自动判分")
    @PostMapping("/submit")
    public R<PracticeResultVO> submitAnswer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PracticeSubmitRequest request) {
        PracticeResultVO result = practiceService.submitAnswer(request, userDetails.getUserId());
        return R.ok(result);
    }

    /**
     * 获取练习记录（分页）
     */
    @Operation(summary = "练习记录", description = "分页获取当前用户的练习记录")
    @GetMapping("/records")
    public R<Page<PracticeRecordVO>> getPracticeRecords(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer isCorrect) {
        Page<PracticeRecordVO> records = practiceService.getUserPracticeRecords(
                userDetails.getUserId(), pageNum, pageSize, questionType, courseId, isCorrect);
        return R.ok(records);
    }

    /**
     * 获取练习统计
     */
    @Operation(summary = "练习统计", description = "获取当前用户的练习统计数据")
    @GetMapping("/stats")
    public R<Map<String, Object>> getPracticeStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> stats = practiceService.getUserPracticeStats(userDetails.getUserId());
        return R.ok(stats);
    }
}