package com.learnplatform.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 试卷 VO
 */
public class ExamPaperVO {
    private Long id;
    private String title;
    private String description;
    private Long courseId;
    private String courseName;
    private Integer totalScore;
    private Integer duration;
    private Integer questionCount;
    private Integer status;
    private Long createBy;
    private LocalDateTime createTime;
    private List<ExamQuestionItem> questions;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public List<ExamQuestionItem> getQuestions() { return questions; }
    public void setQuestions(List<ExamQuestionItem> questions) { this.questions = questions; }

    /** 试卷中的题目项 */
    public static class ExamQuestionItem {
        private Long questionId;
        private Integer sortOrder;
        private Integer score;
        private String content;
        private String questionType;
        private List<QuestionOptionVO> options;

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        public List<QuestionOptionVO> getOptions() { return options; }
        public void setOptions(List<QuestionOptionVO> options) { this.options = options; }
    }
}