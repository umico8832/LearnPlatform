package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.AiCallLogMapper;
import com.learnplatform.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AiCallReservationService {
    private final UserMapper users;
    private final AiCallLogMapper logs;
    private final AiConfig config;

    public AiCallReservationService(UserMapper users, AiCallLogMapper logs, AiConfig config) {
        this.users = users;
        this.logs = logs;
        this.config = config;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public AiCallLog reserve(AiCallLog entry) {
        User user = users.lockAiQuotaUser(entry.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        int limit = user.getAiDailyQuota() == null ? config.getDailyQuota() : user.getAiDailyQuota();
        LocalDateTime now = LocalDateTime.now();
        boolean followup = "question_ask".equals(entry.getFunctionType()) && entry.getRunId() != null
                && logs.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .eq(AiCallLog::getUserId, entry.getUserId()).eq(AiCallLog::getFunctionType, "question_ask")
                .eq(AiCallLog::getRunId, entry.getRunId())) > 0;
        if (limit > 0 && !followup) {
            long count = logs.countQuota(entry.getUserId(), now.toLocalDate().atStartOfDay());
            if (count >= limit) {
                throw new BusinessException(ResultCode.QUOTA_EXCEEDED,
                        "今日 AI 调用次数已达上限（" + limit + " 次），请明天再试");
            }
        }
        entry.setCreateTime(now);
        if (logs.insert(entry) != 1) {
            throw new IllegalStateException("AI call reservation failed");
        }
        return entry;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(AiCallLog entry) {
        if (logs.updateById(entry) != 1) {
            throw new IllegalStateException("AI call completion audit failed");
        }
    }
}
