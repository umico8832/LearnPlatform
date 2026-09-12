package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 管理员调整用户 AI 日配额的不可变审计记录。 */
@TableName("ai_quota_audit_log")
public class AiQuotaAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long adminUserId;
    private Integer previousDailyQuota;
    private Integer newDailyQuota;
    private String reason;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAdminUserId() { return adminUserId; }
    public void setAdminUserId(Long adminUserId) { this.adminUserId = adminUserId; }
    public Integer getPreviousDailyQuota() { return previousDailyQuota; }
    public void setPreviousDailyQuota(Integer previousDailyQuota) { this.previousDailyQuota = previousDailyQuota; }
    public Integer getNewDailyQuota() { return newDailyQuota; }
    public void setNewDailyQuota(Integer newDailyQuota) { this.newDailyQuota = newDailyQuota; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
