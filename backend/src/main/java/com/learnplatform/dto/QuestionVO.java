package com.learnplatform.dto;

import com.learnplatform.entity.Question;

import java.util.List;

/**
 * 题目 VO（包含选项和知识点关联）
 */
public class QuestionVO {
    private Long id;
    private String content;
    private String questionType;
    private Long courseId;
    private String courseName;
    private Integer difficulty;
    private String analysis;
    private String tags;
    private Integer score;
    private Integer status;
    private String sourceType;
    private String sourceReference;
    private String lastReviewTime;
    private String nextReviewTime;
    private Integer reviewRounds;
    private String createTime;
    private String updateTime;
    private List<QuestionOptionVO> options;
    private List<Long> knowledgePointIds;
    private List<String> knowledgePointNames;

    public static QuestionVO fromEntity(Question q) {
        QuestionVO vo = new QuestionVO();
        vo.setId(q.getId());
        vo.setContent(q.getContent());
        vo.setQuestionType(q.getQuestionType());
        vo.setCourseId(q.getCourseId());
        vo.setDifficulty(q.getDifficulty());
        vo.setAnalysis(q.getAnalysis());
        vo.setTags(q.getTags());
        vo.setScore(q.getScore());
        vo.setStatus(q.getStatus());
        if (q.getCreateTime() != null) vo.setCreateTime(q.getCreateTime().toString());
        if (q.getSourceType() != null) vo.setSourceType(q.getSourceType());
        vo.setSourceReference(q.getSourceReference());
        if (q.getLastReviewTime() != null) vo.setLastReviewTime(q.getLastReviewTime().toString());
        if (q.getNextReviewTime() != null) vo.setNextReviewTime(q.getNextReviewTime().toString());
        vo.setReviewRounds(q.getReviewRounds());
        if (q.getUpdateTime() != null) vo.setUpdateTime(q.getUpdateTime().toString());
        return vo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
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
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }
    public String getLastReviewTime() { return lastReviewTime; }
    public void setLastReviewTime(String lastReviewTime) { this.lastReviewTime = lastReviewTime; }
    public String getNextReviewTime() { return nextReviewTime; }
    public void setNextReviewTime(String nextReviewTime) { this.nextReviewTime = nextReviewTime; }
    public Integer getReviewRounds() { return reviewRounds; }
    public void setReviewRounds(Integer reviewRounds) { this.reviewRounds = reviewRounds; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
    public List<QuestionOptionVO> getOptions() { return options; }
    public void setOptions(List<QuestionOptionVO> options) { this.options = options; }
    public List<Long> getKnowledgePointIds() { return knowledgePointIds; }
    public void setKnowledgePointIds(List<Long> knowledgePointIds) { this.knowledgePointIds = knowledgePointIds; }
    public List<String> getKnowledgePointNames() { return knowledgePointNames; }
    public void setKnowledgePointNames(List<String> knowledgePointNames) { this.knowledgePointNames = knowledgePointNames; }
}