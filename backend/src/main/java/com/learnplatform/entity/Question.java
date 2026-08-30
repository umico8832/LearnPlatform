package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 题目实体
 */
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String content;

    private String questionType;

    private Long courseId;

    private Integer difficulty;

    private String analysis;

    private String tags;

    private Integer score;

    private Integer status;

    private Long createBy;

    private Long ownerUserId;

    private String visibility;

    /** 来源类型：MANUAL/SUBMISSION/EXCEL_IMPORT/MARKDOWN_IMPORT/AI_GENERATED */
    private String sourceType;

    /** 来源引用（投稿ID/导入批次ID等） */
    private String sourceReference;

    private Long originQuestionId;

    /** 最近复审时间 */
    private LocalDateTime lastReviewTime;

    /** 下次复审时间 */
    private LocalDateTime nextReviewTime;

    /** 累计复审轮次 */
    private Integer reviewRounds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }
    public Long getOriginQuestionId() { return originQuestionId; }
    public void setOriginQuestionId(Long originQuestionId) { this.originQuestionId = originQuestionId; }
    public LocalDateTime getLastReviewTime() { return lastReviewTime; }
    public void setLastReviewTime(LocalDateTime lastReviewTime) { this.lastReviewTime = lastReviewTime; }
    public LocalDateTime getNextReviewTime() { return nextReviewTime; }
    public void setNextReviewTime(LocalDateTime nextReviewTime) { this.nextReviewTime = nextReviewTime; }
    public Integer getReviewRounds() { return reviewRounds; }
    public void setReviewRounds(Integer reviewRounds) { this.reviewRounds = reviewRounds; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
