package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.PracticeRecordVO;
import com.learnplatform.dto.PracticeResultVO;
import com.learnplatform.dto.PracticeSubmitRequest;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AdaptivePracticeService;
import com.learnplatform.service.PracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    private final AdaptivePracticeService adaptivePracticeService;

    public PracticeController(PracticeService practiceService,
                              AdaptivePracticeService adaptivePracticeService) {
        this.practiceService = practiceService;
        this.adaptivePracticeService = adaptivePracticeService;
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

    /**
     * 获取错题重练题目
     * 从错题本中按掌握程度筛选并随机抽取题目
     */
    @Operation(summary = "错题重练", description = "从错题本中随机抽取题目用于重练")
    @GetMapping("/wrong-questions")
    public R<List<QuestionVO>> getWrongQuestionPractice(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Integer masteryLevel,
            @RequestParam(required = false) Integer count) {
        List<QuestionVO> questions = practiceService.getWrongQuestionPractice(
                userDetails.getUserId(), masteryLevel, count);
        return R.ok(questions);
    }

    /**
     * 获取收藏题练习题目
     * 从当前用户收藏夹中随机抽取题目
     */
    @Operation(summary = "收藏题练习", description = "从当前用户收藏题目中随机抽取题目用于练习")
    @GetMapping("/favorites")
    public R<List<QuestionVO>> getFavoritePractice(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Integer count,
            @RequestParam(required = false) Long questionId) {
        List<QuestionVO> questions = practiceService.getFavoritePractice(
                userDetails.getUserId(), count, questionId);
        return R.ok(questions);
    }

    /**
     * 自适应智能推荐题目
     * 根据用户各难度级别的历史正确率动态调整题目难度分布
     */
    @Operation(summary = "智能推荐题目", description = "根据用户答题历史自适应推荐难度")
    @GetMapping("/adaptive")
    public R<List<QuestionVO>> getAdaptiveQuestions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long knowledgePointId,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Integer count) {
        List<QuestionVO> questions = adaptivePracticeService.getAdaptiveQuestions(
                userDetails.getUserId(), courseId, knowledgePointId,
                questionType, count);
        return R.ok(questions);
    }

    /**
     * 获取自适应推荐摘要
     * 返回用户各难度级别的答题统计和推荐权重
     */
    @Operation(summary = "自适应推荐摘要", description = "获取用户各难度答题表现和推荐权重")
    @GetMapping("/adaptive/summary")
    public R<Map<String, Object>> getAdaptiveSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> summary = adaptivePracticeService.getAdaptiveSummary(
                userDetails.getUserId());
        return R.ok(summary);
    }
}
