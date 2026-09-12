package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.FavoriteQuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 题目收藏控制器（用户端）
 */
@Tag(name = "题目收藏", description = "用户端题目收藏管理相关接口")
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /**
     * 收藏题目
     */
    @Operation(summary = "收藏题目", description = "收藏指定题目")
    @PostMapping("/{questionId}")
    public R<Void> addFavorite(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long questionId) {
        favoriteService.addFavorite(userDetails.getUserId(), questionId);
        return R.ok(null);
    }

    /**
     * 取消收藏
     */
    @Operation(summary = "取消收藏", description = "取消收藏指定题目")
    @DeleteMapping("/{questionId}")
    public R<Void> removeFavorite(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long questionId) {
        favoriteService.removeFavorite(userDetails.getUserId(), questionId);
        return R.ok(null);
    }

    /**
     * 检查是否已收藏
     */
    @Operation(summary = "检查收藏状态", description = "检查指定题目是否已被当前用户收藏")
    @GetMapping("/{questionId}/status")
    public R<Map<String, Boolean>> checkFavorite(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long questionId) {
        boolean isFavorite = favoriteService.isFavorite(userDetails.getUserId(), questionId);
        return R.ok(Map.of("isFavorite", isFavorite));
    }

    /**
     * 获取收藏列表（分页）
     */
    @Operation(summary = "收藏列表", description = "分页获取当前用户的收藏题目列表")
    @GetMapping
    public R<Page<FavoriteQuestionVO>> getFavorites(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<FavoriteQuestionVO> page = favoriteService.getFavorites(
                userDetails.getUserId(), pageNum, pageSize);
        return R.ok(page);
    }

    /**
     * 获取收藏题目 ID 列表
     */
    @Operation(summary = "收藏题目ID列表", description = "获取当前用户收藏的所有题目ID（用于前端批量判断）")
    @GetMapping("/ids")
    public R<List<Long>> getFavoriteIds(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<Long> ids = favoriteService.getFavoriteQuestionIds(userDetails.getUserId());
        return R.ok(ids);
    }
}
