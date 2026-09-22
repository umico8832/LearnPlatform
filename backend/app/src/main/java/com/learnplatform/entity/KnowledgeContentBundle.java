package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

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
}
