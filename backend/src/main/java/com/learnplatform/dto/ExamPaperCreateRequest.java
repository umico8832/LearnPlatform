package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * 创建/更新试卷请求
 */
public class ExamPaperCreateRequest {
    @NotBlank(message = "试卷名称不能为空")
    private String title;
    private String description;
    @NotNull(message = "课程ID不能为空")
    private Long courseId;
    @NotNull(message = "考试时长不能为空")
    @Positive(message = "考试时长必须大于0")
    private Integer duration;
    private Integer status;
    private String paperType;
    private String examName;
    private Integer examYear;
    private String sourceReference;
    private Boolean sourceVerified;
    private List<QuestionItem> questions;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getPaperType() { return paperType; }
    public void setPaperType(String paperType) { this.paperType = paperType; }
    public String getExamName() { return examName; }
    public void setExamName(String examName) { this.examName = examName; }
    public Integer getExamYear() { return examYear; }
    public void setExamYear(Integer examYear) { this.examYear = examYear; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }
    public Boolean getSourceVerified() { return sourceVerified; }
    public void setSourceVerified(Boolean sourceVerified) { this.sourceVerified = sourceVerified; }
    public List<QuestionItem> getQuestions() { return questions; }
    public void setQuestions(List<QuestionItem> questions) { this.questions = questions; }

    public static class QuestionItem {
        private Long questionId;
        private Integer sortOrder;
        private Integer score;
        private String sectionTitle;
        private String majorQuestionNumber;
        private String minorQuestionNumber;
        private String subquestionNumber;
        private String displayNumber;

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public String getSectionTitle() { return sectionTitle; }
        public void setSectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; }
        public String getMajorQuestionNumber() { return majorQuestionNumber; }
        public void setMajorQuestionNumber(String majorQuestionNumber) {
            this.majorQuestionNumber = majorQuestionNumber;
        }
        public String getMinorQuestionNumber() { return minorQuestionNumber; }
        public void setMinorQuestionNumber(String minorQuestionNumber) {
            this.minorQuestionNumber = minorQuestionNumber;
        }
        public String getSubquestionNumber() { return subquestionNumber; }
        public void setSubquestionNumber(String subquestionNumber) { this.subquestionNumber = subquestionNumber; }
        public String getDisplayNumber() { return displayNumber; }
        public void setDisplayNumber(String displayNumber) { this.displayNumber = displayNumber; }
    }
}
