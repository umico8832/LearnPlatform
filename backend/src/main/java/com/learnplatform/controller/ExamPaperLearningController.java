package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.ExamLearningAnswerRequest;
import com.learnplatform.dto.ExamLearningAnswerResultVO;
import com.learnplatform.dto.ExamLearningSessionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamPaperLearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端试卷逐题学习会话入口。
 */
@Tag(name = "试卷学习", description = "用户端试卷逐题学习接口")
@RestController
@RequestMapping("/api/exam")
public class ExamPaperLearningController {

    private final ExamPaperLearningService examPaperLearningService;

    public ExamPaperLearningController(ExamPaperLearningService examPaperLearningService) {
        this.examPaperLearningService = examPaperLearningService;
    }

    @Operation(summary = "开始或恢复试卷学习会话")
    @PostMapping("/papers/{paperId}/learning-sessions")
    public R<ExamLearningSessionVO> startLearningSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paperId) {
        return R.ok(examPaperLearningService.startSession(paperId, userDetails.getUserId()));
    }

    @Operation(summary = "获取本人试卷学习会话")
    @GetMapping("/learning-sessions/{sessionId}")
    public R<ExamLearningSessionVO> getLearningSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId) {
        return R.ok(examPaperLearningService.getSession(sessionId, userDetails.getUserId()));
    }

    @Operation(summary = "提交试卷学习逐题答案")
    @PostMapping("/learning-sessions/{sessionId}/answers")
    public R<ExamLearningAnswerResultVO> submitLearningAnswer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId,
            @Valid @RequestBody ExamLearningAnswerRequest request) {
        return R.ok(examPaperLearningService.submitAnswer(sessionId, request, userDetails.getUserId()));
    }

    @Operation(summary = "完成本轮试卷学习")
    @PostMapping("/learning-sessions/{sessionId}/complete")
    public R<ExamLearningSessionVO> completeLearningSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId) {
        return R.ok(examPaperLearningService.completeSession(sessionId, userDetails.getUserId()));
    }
}
