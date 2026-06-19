package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.GlobalSearchResultVO;
import com.learnplatform.service.GlobalSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局搜索接口
 * Phase 18：全局搜索与快捷导航
 */
@Tag(name = "全局搜索", description = "跨题目、课程、知识点的全局搜索接口")
@RestController
@RequestMapping("/api/search")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    public GlobalSearchController(GlobalSearchService globalSearchService) {
        this.globalSearchService = globalSearchService;
    }

    @Operation(summary = "全局搜索", description = "在题目内容、课程名称、知识点名称中模糊搜索，结果按类型分组返回")
    @GetMapping
    public R<GlobalSearchResultVO> search(
            @Parameter(description = "搜索关键词", required = true)
            @RequestParam String keyword,
            @Parameter(description = "每类结果最大条数（默认 5，最大 20）")
            @RequestParam(required = false) Integer limit) {
        GlobalSearchResultVO result = globalSearchService.search(keyword, limit);
        return R.ok(result);
    }
}