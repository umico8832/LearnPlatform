package com.learnplatform.dto;

import com.learnplatform.entity.QuestionCorrectionReport;

import java.time.LocalDateTime;

/** 题目纠错反馈视图对象。 */
public class QuestionCorrectionReportVO {
    private Long id;
    private Long questionId;
    private String questionContent;
    private Long reporterId;
    private String reporterName;
    private String reportType;
    private String description;
    private String status;
    private Long handlerId;
    private String handlerName;
    private String handlerComment;
    private LocalDateTime handledTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static QuestionCorrectionReportVO fromEntity(QuestionCorrectionReport report) {
        QuestionCorrectionReportVO vo = new QuestionCorrectionReportVO();
        vo.setId(report.getId());
        vo.setQuestionId(report.getQuestionId());
        vo.setReporterId(report.getReporterId());
        vo.setReportType(report.getReportType());
        vo.setDescription(report.getDescription());
        vo.setStatus(report.getStatus());
        vo.setHandlerId(report.getHandlerId());
        vo.setHandlerComment(report.getHandlerComment());
        vo.setHandledTime(report.getHandledTime());
        vo.setCreateTime(report.getCreateTime());
        vo.setUpdateTime(report.getUpdateTime());
        return vo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long handlerId) { this.handlerId = handlerId; }
    public String getHandlerName() { return handlerName; }
    public void setHandlerName(String handlerName) { this.handlerName = handlerName; }
    public String getHandlerComment() { return handlerComment; }
    public void setHandlerComment(String handlerComment) { this.handlerComment = handlerComment; }
    public LocalDateTime getHandledTime() { return handledTime; }
    public void setHandledTime(LocalDateTime handledTime) { this.handledTime = handledTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
