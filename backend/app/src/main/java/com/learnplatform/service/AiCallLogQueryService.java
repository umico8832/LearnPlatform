package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.AiCallLogStatsVO;
import com.learnplatform.dto.AiCallLogVO;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.mapper.AiCallLogMapper;
import org.springframework.stereotype.Service;

@Service
public class AiCallLogQueryService {

    private final AiCallLogMapper aiCallLogMapper;

    public AiCallLogQueryService(AiCallLogMapper aiCallLogMapper) {
        this.aiCallLogMapper = aiCallLogMapper;
    }

    public Page<AiCallLogVO> listLogs(int page, int size, String functionType, Integer status) {
        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<>();
        if (functionType != null && !functionType.isBlank()) {
            wrapper.eq(AiCallLog::getFunctionType, functionType);
        }
        if (status != null) {
            wrapper.eq(AiCallLog::getStatus, status);
        }
        wrapper.and(query -> query.isNull(AiCallLog::getOutcome).or().ne(AiCallLog::getOutcome, "RUNNING"));
        wrapper.orderByDesc(AiCallLog::getCreateTime);

        Page<AiCallLog> result = aiCallLogMapper.selectPage(new Page<>(page, size), wrapper);
        Page<AiCallLogVO> response = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        response.setRecords(result.getRecords().stream().map(AiCallLogVO::fromEntity).toList());
        return response;
    }

    private LambdaQueryWrapper<AiCallLog> completed() {
        return new LambdaQueryWrapper<AiCallLog>()
                .and(query -> query.isNull(AiCallLog::getOutcome).or().ne(AiCallLog::getOutcome, "RUNNING"));
    }

    public AiCallLogStatsVO getStats() {
        long total = aiCallLogMapper.selectCount(completed());
        long success = aiCallLogMapper.selectCount(
                completed().eq(AiCallLog::getStatus, 1));
        long fail = aiCallLogMapper.selectCount(
                completed().eq(AiCallLog::getStatus, 0));
        return new AiCallLogStatsVO(total, success, fail);
    }
}
