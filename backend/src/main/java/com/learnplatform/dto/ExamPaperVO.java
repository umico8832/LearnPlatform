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
    private Long ownerUserId;
    private String visibility;
    private String paperType;
    private String examName;
    private Integer examYear;
    private String sourceReference;
    private Boolean sourceVerified;
    private String importStatus;
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
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
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
    public String getImportStatus() { return importStatus; }
    public void setImportStatus(String importStatus) { this.importStatus = importStatus; }
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
        private String sectionTitle;
        private String majorQuestionNumber;
        private String minorQuestionNumber;
        private String subquestionNumber;
        private String displayNumber;
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
        public String getSectionTitle() { return sectionTitle; }
        public void setSectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; }
        public String getMajorQuestionNumber() { return majorQuestionNumber; }
        public void setMajorQuestionNumber(String majorQuestionNumber) { this.majorQuestionNumber = majorQuestionNumber; }
        public String getMinorQuestionNumber() { return minorQuestionNumber; }
        public void setMinorQuestionNumber(String minorQuestionNumber) { this.minorQuestionNumber = minorQuestionNumber; }
        public String getSubquestionNumber() { return subquestionNumber; }
        public void setSubquestionNumber(String subquestionNumber) { this.subquestionNumber = subquestionNumber; }
        public String getDisplayNumber() { return displayNumber; }
        public void setDisplayNumber(String displayNumber) { this.displayNumber = displayNumber; }
        public List<QuestionOptionVO> getOptions() { return options; }
        public void setOptions(List<QuestionOptionVO> options) { this.options = options; }
    }
}
