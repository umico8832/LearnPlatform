package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.AiCallLogStatsVO;
import com.learnplatform.dto.AiCallLogVO;
import com.learnplatform.service.AiCallLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 AI 调用日志查询
 */
@Tag(name = "管理端-AI 调用日志", description = "AI 调用日志查询与统计")
@RestController
@RequestMapping("/api/admin/ai-logs")
public class AdminAiCallLogController {

    private final AiCallLogQueryService aiCallLogQueryService;

    public AdminAiCallLogController(AiCallLogQueryService aiCallLogQueryService) {
        this.aiCallLogQueryService = aiCallLogQueryService;
    }

    @Operation(summary = "分页查询 AI 调用日志", description = "支持按功能类型、状态筛选")
    @GetMapping
    public R<Page<AiCallLogVO>> listLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String functionType,
            @RequestParam(required = false) Integer status) {

        return R.ok(aiCallLogQueryService.listLogs(page, size, functionType, status));
    }

    @Operation(summary = "AI 调用统计概览", description = "总调用次数、成功/失败次数、平均耗时")
    @GetMapping("/stats")
    public R<AiCallLogStatsVO> getStats() {
        return R.ok(aiCallLogQueryService.getStats());
    }
}
