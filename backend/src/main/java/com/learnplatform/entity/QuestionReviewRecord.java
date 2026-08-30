package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 题目复审记录实体
 */
@TableName("question_review_record")
public class QuestionReviewRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Long reviewerId;

    /** 复审类型：REGULAR-定期复审 TRIGGERED-触发复审 INITIAL-入库初审 */
    private String reviewType;

    /** 复审动作：APPROVE-通过 REVISE-修订 REJECT-标记废弃 */
    private String action;

    /** 复审前题干（快照） */
    private String oldContent;

    /** 复审后题干（如有修订） */
    private String newContent;

    /** 复审前难度 */
    private Integer oldDifficulty;

    /** 复审后难度（如有修订） */
    private Integer newDifficulty;

    /** 复审意见 */
    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewType() { return reviewType; }
    public void setReviewType(String reviewType) { this.reviewType = reviewType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOldContent() { return oldContent; }
    public void setOldContent(String oldContent) { this.oldContent = oldContent; }
    public String getNewContent() { return newContent; }
    public void setNewContent(String newContent) { this.newContent = newContent; }
    public Integer getOldDifficulty() { return oldDifficulty; }
    public void setOldDifficulty(Integer oldDifficulty) { this.oldDifficulty = oldDifficulty; }
    public Integer getNewDifficulty() { return newDifficulty; }
    public void setNewDifficulty(Integer newDifficulty) { this.newDifficulty = newDifficulty; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
