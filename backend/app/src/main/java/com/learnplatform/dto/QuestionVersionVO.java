package com.learnplatform.dto;

import com.learnplatform.entity.QuestionVersion;

import java.time.LocalDateTime;

/** 题目版本记录视图对象。 */
public class QuestionVersionVO {
    private Long id;
    private Long questionId;
    private Integer versionNo;
    private String changeType;
    private Long operatorId;
    private String operatorName;
    private String changeSummary;
    private String snapshotBefore;
    private String snapshotAfter;
    private LocalDateTime createTime;

    public static QuestionVersionVO fromEntity(QuestionVersion version) {
        QuestionVersionVO vo = new QuestionVersionVO();
        vo.setId(version.getId());
        vo.setQuestionId(version.getQuestionId());
        vo.setVersionNo(version.getVersionNo());
        vo.setChangeType(version.getChangeType());
        vo.setOperatorId(version.getOperatorId());
        vo.setChangeSummary(version.getChangeSummary());
        vo.setSnapshotBefore(version.getSnapshotBefore());
        vo.setSnapshotAfter(version.getSnapshotAfter());
        vo.setCreateTime(version.getCreateTime());
        return vo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
    public String getSnapshotBefore() { return snapshotBefore; }
    public void setSnapshotBefore(String snapshotBefore) { this.snapshotBefore = snapshotBefore; }
    public String getSnapshotAfter() { return snapshotAfter; }
    public void setSnapshotAfter(String snapshotAfter) { this.snapshotAfter = snapshotAfter; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
