package com.learnplatform.dto;

import java.time.LocalDateTime;

public class PrivateExamSourceVO {
    private Long paperId;
    private String sourceName;
    private String sourceFormat;
    private String contentHash;
    private String originalContent;
    private Boolean originalFileAvailable;
    private LocalDateTime createTime;

    public Long getPaperId() { return paperId; }
    public void setPaperId(Long paperId) { this.paperId = paperId; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceFormat() { return sourceFormat; }
    public void setSourceFormat(String sourceFormat) { this.sourceFormat = sourceFormat; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getOriginalContent() { return originalContent; }
    public void setOriginalContent(String originalContent) { this.originalContent = originalContent; }
    public Boolean getOriginalFileAvailable() { return originalFileAvailable; }
    public void setOriginalFileAvailable(Boolean originalFileAvailable) { this.originalFileAvailable = originalFileAvailable; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
