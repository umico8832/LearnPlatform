package com.learnplatform.dto;

import java.time.LocalDateTime;

/**
 * 题目 AI 学习资产 VO
 * 用于返回一道题的所有 AI 学习资产
 */
public class QuestionLearningAssetVO {

    /** 资产ID */
    private Long id;

    /** 题目ID */
    private Long questionId;

    /** 资产类型 */
    private String assetType;

    /** 资产类型中文标签 */
    private String assetTypeLabel;

    /** AI 生成的 Markdown 内容 */
    private String content;

    /** AI 模型名称 */
    private String model;

    /** 创建时间 */
    private LocalDateTime createTime;

    public QuestionLearningAssetVO() {}

    public QuestionLearningAssetVO(Long id, Long questionId, String assetType,
                                   String assetTypeLabel, String content,
                                   String model, LocalDateTime createTime) {
        this.id = id;
        this.questionId = questionId;
        this.assetType = assetType;
        this.assetTypeLabel = assetTypeLabel;
        this.content = content;
        this.model = model;
        this.createTime = createTime;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getAssetTypeLabel() { return assetTypeLabel; }
    public void setAssetTypeLabel(String assetTypeLabel) { this.assetTypeLabel = assetTypeLabel; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}