package com.learnplatform.dto;

import com.learnplatform.entity.AiQuotaAuditLog;

import java.time.LocalDateTime;

public record AiQuotaAuditLogVO(
        Long id,
        Long userId,
        Long adminUserId,
        Integer previousDailyQuota,
        Integer newDailyQuota,
        String reason,
        LocalDateTime createTime
) {
    public static AiQuotaAuditLogVO fromEntity(AiQuotaAuditLog entity) {
        return new AiQuotaAuditLogVO(
                entity.getId(),
                entity.getUserId(),
                entity.getAdminUserId(),
                entity.getPreviousDailyQuota(),
                entity.getNewDailyQuota(),
                entity.getReason(),
                entity.getCreateTime()
        );
    }
}
