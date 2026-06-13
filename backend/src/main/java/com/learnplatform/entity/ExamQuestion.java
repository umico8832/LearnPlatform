package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.*;

/**
 * 试卷-题目关联实体
 */
@TableName("exam_question")
public class ExamQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examPaperId;

    private Long questionId;

    private Integer sortOrder;

    private Integer score;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExamPaperId() { return examPaperId; }
    public void setExamPaperId(Long examPaperId) { this.examPaperId = examPaperId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
}