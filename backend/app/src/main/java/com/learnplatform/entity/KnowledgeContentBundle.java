package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("knowledge_content_bundle")
public class KnowledgeContentBundle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String courseKey;
    private String bundleVersion;
    private String manifestHash;
    private String sourceRevision;
    private String sourceQualityStatus;
    private String reviewStatus;
    private Integer chunkCount;
    private Long importedBy;
    private LocalDateTime importedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getCourseKey() { return courseKey; }
    public void setCourseKey(String value) { courseKey = value; }
    public String getBundleVersion() { return bundleVersion; }
    public void setBundleVersion(String value) { bundleVersion = value; }
    public String getManifestHash() { return manifestHash; }
    public void setManifestHash(String value) { manifestHash = value; }
    public String getSourceRevision() { return sourceRevision; }
    public void setSourceRevision(String value) { sourceRevision = value; }
    public String getSourceQualityStatus() { return sourceQualityStatus; }
    public void setSourceQualityStatus(String value) { sourceQualityStatus = value; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String value) { reviewStatus = value; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer value) { chunkCount = value; }
    public Long getImportedBy() { return importedBy; }
    public void setImportedBy(Long value) { importedBy = value; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime value) { importedAt = value; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long value) { reviewedBy = value; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime value) { reviewedAt = value; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String value) { reviewNote = value; }
}
