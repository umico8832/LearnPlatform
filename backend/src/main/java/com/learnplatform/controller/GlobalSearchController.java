package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.GlobalSearchResultVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.GlobalSearchService;
import com.learnplatform.service.SearchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局搜索接口
 * Phase 18：全局搜索与快捷导航
 */
@Tag(name = "全局搜索", description = "跨题目、课程、知识点的全局搜索接口")
@RestController
@RequestMapping("/api/search")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;
    private final SearchHistoryService searchHistoryService;

    public GlobalSearchController(GlobalSearchService globalSearchService,
                                  SearchHistoryService searchHistoryService) {
        this.globalSearchService = globalSearchService;
        this.searchHistoryService = searchHistoryService;
    }

    @Operation(summary = "全局搜索", description = "在题目内容、课程名称、知识点名称中模糊搜索，结果按类型分组返回。同时记录搜索历史和热门搜索。")
    @GetMapping
    public R<GlobalSearchResultVO> search(
            @Parameter(description = "搜索关键词", required = true)
            @RequestParam String keyword,
            @Parameter(description = "每类结果最大条数（默认 5，最大 20）")
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        GlobalSearchResultVO result = globalSearchService.search(keyword, limit);
        // 记录搜索历史和热门关键词
        if (userDetails != null) {
            searchHistoryService.recordSearch(userDetails.getUserId(), keyword);
        }
        return R.ok(result);
    }

    @Operation(summary = "获取搜索历史和热门搜索", description = "返回当前用户的搜索历史和全局热门搜索关键词")
    @GetMapping("/suggestions")
    public R<Map<String, Object>> getSuggestions(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> result = new HashMap<>();
        if (userDetails != null) {
            result.put("history", searchHistoryService.getUserHistory(userDetails.getUserId()));
        } else {
            result.put("history", List.of());
        }
        result.put("hotKeywords", searchHistoryService.getHotKeywords());
        return R.ok(result);
    }

    @Operation(summary = "清除搜索历史", description = "清除当前用户的全部搜索历史")
    @DeleteMapping("/history")
    public R<Void> clearHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails != null) {
            searchHistoryService.clearUserHistory(userDetails.getUserId());
        }
        return R.ok();
    }

    @Operation(summary = "删除单条搜索历史", description = "删除当前用户搜索历史中的某个关键词")
    @DeleteMapping("/history/item")
    public R<Void> removeHistoryItem(
            @Parameter(description = "要删除的关键词", required = true)
            @RequestParam String keyword,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails != null) {
            searchHistoryService.removeUserHistoryItem(userDetails.getUserId(), keyword);
        }
        return R.ok();
    }
}