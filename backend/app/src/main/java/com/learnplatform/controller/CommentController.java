package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.CommentRequest;
import com.learnplatform.dto.CommentVO;
import com.learnplatform.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题目讨论/评论接口
 */
@Tag(name = "题目讨论", description = "题目评论、回复和点赞功能")
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "获取题目评论列表")
    @GetMapping("/question/{questionId}")
    public R<List<CommentVO>> getComments(
            @PathVariable Long questionId,
            @AuthenticationPrincipal Long userId) {
        return R.ok(commentService.getComments(questionId, userId));
    }

    @Operation(summary = "发表评论")
    @PostMapping
    public R<CommentVO> addComment(
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal Long userId) {
        return R.ok(commentService.addComment(request, userId));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{commentId}")
    public R<Void> deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal Long userId) {
        commentService.deleteComment(commentId, userId);
        return R.ok();
    }

    @Operation(summary = "点赞/取消点赞")
    @PostMapping("/{commentId}/like")
    public R<Boolean> toggleLike(
            @PathVariable Long commentId,
            @AuthenticationPrincipal Long userId) {
        return R.ok(commentService.toggleLike(commentId, userId));
    }

    @Operation(summary = "获取题目评论数")
    @GetMapping("/count/{questionId}")
    public R<Integer> getCommentCount(
            @PathVariable Long questionId) {
        return R.ok(commentService.getCommentCount(questionId));
    }
}
