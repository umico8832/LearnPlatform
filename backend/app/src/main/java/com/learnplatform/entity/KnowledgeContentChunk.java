package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("knowledge_content_chunk")
public class KnowledgeContentChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bundleId;
    private String chunkKey;
    private String conceptKey;
    private String title;
    private String content;
    private String contentHash;
    private String metadataJson;
    private String sourceQualityStatus;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getBundleId() { return bundleId; }
    public void setBundleId(Long value) { bundleId = value; }
    public String getChunkKey() { return chunkKey; }
    public void setChunkKey(String value) { chunkKey = value; }
    public String getConceptKey() { return conceptKey; }
    public void setConceptKey(String value) { conceptKey = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String value) { contentHash = value; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String value) { metadataJson = value; }
    public String getSourceQualityStatus() { return sourceQualityStatus; }
    public void setSourceQualityStatus(String value) { sourceQualityStatus = value; }
}
