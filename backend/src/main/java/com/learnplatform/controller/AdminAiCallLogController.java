package com.learnplatform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.mapper.AiCallLogMapper;
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

    private final AiCallLogMapper aiCallLogMapper;

    public AdminAiCallLogController(AiCallLogMapper aiCallLogMapper) {
        this.aiCallLogMapper = aiCallLogMapper;
    }

    @Operation(summary = "分页查询 AI 调用日志", description = "支持按功能类型、状态筛选")
    @GetMapping
    public R<Page<AiCallLog>> listLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String functionType,
            @RequestParam(required = false) Integer status) {

        Page<AiCallLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<>();

        if (functionType != null && !functionType.isBlank()) {
            wrapper.eq(AiCallLog::getFunctionType, functionType);
        }
        if (status != null) {
            wrapper.eq(AiCallLog::getStatus, status);
        }
        wrapper.orderByDesc(AiCallLog::getCreateTime);

        Page<AiCallLog> result = aiCallLogMapper.selectPage(pageParam, wrapper);
        return R.ok(result);
    }

    @Operation(summary = "AI 调用统计概览", description = "总调用次数、成功/失败次数、平均耗时")
    @GetMapping("/stats")
    public R<AiLogStats> getStats() {
        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<>();
        long total = aiCallLogMapper.selectCount(wrapper);

        wrapper.clear();
        wrapper.eq(AiCallLog::getStatus, 1);
        long success = aiCallLogMapper.selectCount(wrapper);

        wrapper.clear();
        wrapper.eq(AiCallLog::getStatus, 0);
        long fail = aiCallLogMapper.selectCount(wrapper);

        AiLogStats stats = new AiLogStats();
        stats.setTotal(total);
        stats.setSuccess(success);
        stats.setFail(fail);
        return R.ok(stats);
    }

    /**
     * AI 日志统计 VO
     */
    public static class AiLogStats {
        private long total;
        private long success;
        private long fail;

        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }

        public long getSuccess() { return success; }
        public void setSuccess(long success) { this.success = success; }

        public long getFail() { return fail; }
        public void setFail(long fail) { this.fail = fail; }
    }
}