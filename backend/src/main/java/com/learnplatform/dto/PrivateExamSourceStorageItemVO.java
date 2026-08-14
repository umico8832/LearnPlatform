package com.learnplatform.dto;

import java.time.LocalDateTime;

public class PrivateExamSourceStorageItemVO {
    private Long id;
    private String sourceName;
    private String sourceFormat;
    private String sourceMediaType;
    private Long sourceSize;
    private LocalDateTime createTime;
    private String associationType;
    private Long associationId;
    private String associationTitle;
    private String associationStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceFormat() { return sourceFormat; }
    public void setSourceFormat(String sourceFormat) { this.sourceFormat = sourceFormat; }
    public String getSourceMediaType() { return sourceMediaType; }
    public void setSourceMediaType(String sourceMediaType) { this.sourceMediaType = sourceMediaType; }
    public Long getSourceSize() { return sourceSize; }
    public void setSourceSize(Long sourceSize) { this.sourceSize = sourceSize; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getAssociationType() { return associationType; }
    public void setAssociationType(String associationType) { this.associationType = associationType; }
    public Long getAssociationId() { return associationId; }
    public void setAssociationId(Long associationId) { this.associationId = associationId; }
    public String getAssociationTitle() { return associationTitle; }
    public void setAssociationTitle(String associationTitle) { this.associationTitle = associationTitle; }
    public String getAssociationStatus() { return associationStatus; }
    public void setAssociationStatus(String associationStatus) { this.associationStatus = associationStatus; }
}
