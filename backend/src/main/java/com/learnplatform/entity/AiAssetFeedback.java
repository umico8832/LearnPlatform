package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 学习资产质量反馈实体
 * 用户对 AI 生成的学习资产内容给出有帮助/无帮助反馈
 */
@TableName("ai_asset_feedback")
public class AiAssetFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 题目ID */
    private Long questionId;

    /** 资产类型 */
    private String assetType;

    /** 用户ID */
    private Long userId;

    /** 是否有帮助：1-有帮助 0-无帮助 */
    private Boolean helpful;

    /** 用户补充反馈文字（可选） */
    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Boolean getHelpful() { return helpful; }
    public void setHelpful(Boolean helpful) { this.helpful = helpful; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
