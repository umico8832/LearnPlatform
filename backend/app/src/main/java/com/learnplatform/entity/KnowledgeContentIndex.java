package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("knowledge_content_index")
public class KnowledgeContentIndex {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bundleId;
    private String indexKey;
    private String model;
    private Integer dimensions;
    private String collectionName;
    private String vectorEndpointHash;
    private String status;
    private String runKey;
    private LocalDateTime leaseUntil;
    private Integer indexedCount;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getBundleId() { return bundleId; }
    public void setBundleId(Long value) { bundleId = value; }
    public String getIndexKey() { return indexKey; }
    public void setIndexKey(String value) { indexKey = value; }
    public String getModel() { return model; }
    public void setModel(String value) { model = value; }
    public Integer getDimensions() { return dimensions; }
    public void setDimensions(Integer value) { dimensions = value; }
    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String value) { collectionName = value; }
    public String getVectorEndpointHash() { return vectorEndpointHash; }
    public void setVectorEndpointHash(String value) { vectorEndpointHash = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getRunKey() { return runKey; }
    public void setRunKey(String value) { runKey = value; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime value) { leaseUntil = value; }
    public Integer getIndexedCount() { return indexedCount; }
    public void setIndexedCount(Integer value) { indexedCount = value; }
}
