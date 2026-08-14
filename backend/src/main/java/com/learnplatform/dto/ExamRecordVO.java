package com.learnplatform.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 考试记录 VO
 */
public class ExamRecordVO {
    private Long id;
    private Long examPaperId;
    private String examTitle;
    private Long courseId;
    private String paperType;
    private String examName;
    private Integer examYear;
    private String sourceReference;
    private Boolean sourceVerified;
    private LocalDateTime startTime;
    private OffsetDateTime deadline;
    private OffsetDateTime serverTime;
    private LocalDateTime endTime;
    private Integer score;
    private Integer totalScore;
    private Integer status;
    private Integer duration;
    private List<ExamAnswerVO> answers;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExamPaperId() { return examPaperId; }
    public void setExamPaperId(Long examPaperId) { this.examPaperId = examPaperId; }
    public String getExamTitle() { return examTitle; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
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
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public OffsetDateTime getDeadline() { return deadline; }
    public void setDeadline(OffsetDateTime deadline) { this.deadline = deadline; }
    public OffsetDateTime getServerTime() { return serverTime; }
    public void setServerTime(OffsetDateTime serverTime) { this.serverTime = serverTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public List<ExamAnswerVO> getAnswers() { return answers; }
    public void setAnswers(List<ExamAnswerVO> answers) { this.answers = answers; }

    public static class ExamAnswerVO {
        private Long questionId;
        private String content;
        private String questionType;
        private Integer sortOrder;
        private Integer fullScore;
        private String sectionTitle;
        private String majorQuestionNumber;
        private String minorQuestionNumber;
        private String subquestionNumber;
        private String displayNumber;
        private String userAnswer;
        private Integer isCorrect;
        private Integer score;
        private String correctAnswer;
        private String analysis;
        private String gradingStatus;
        private String reviewComment;
        private String reviewDetailJson;

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public Integer getFullScore() { return fullScore; }
        public void setFullScore(Integer fullScore) { this.fullScore = fullScore; }
        public String getSectionTitle() { return sectionTitle; }
        public void setSectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; }
        public String getMajorQuestionNumber() { return majorQuestionNumber; }
        public void setMajorQuestionNumber(String value) { majorQuestionNumber = value; }
        public String getMinorQuestionNumber() { return minorQuestionNumber; }
        public void setMinorQuestionNumber(String value) { minorQuestionNumber = value; }
        public String getSubquestionNumber() { return subquestionNumber; }
        public void setSubquestionNumber(String value) { subquestionNumber = value; }
        public String getDisplayNumber() { return displayNumber; }
        public void setDisplayNumber(String displayNumber) { this.displayNumber = displayNumber; }
        public String getUserAnswer() { return userAnswer; }
        public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
        public Integer getIsCorrect() { return isCorrect; }
        public void setIsCorrect(Integer isCorrect) { this.isCorrect = isCorrect; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public String getCorrectAnswer() { return correctAnswer; }
        public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
        public String getAnalysis() { return analysis; }
        public void setAnalysis(String analysis) { this.analysis = analysis; }
        public String getGradingStatus() { return gradingStatus; }
        public void setGradingStatus(String gradingStatus) { this.gradingStatus = gradingStatus; }
        public String getReviewComment() { return reviewComment; }
        public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
        public String getReviewDetailJson() { return reviewDetailJson; }
        public void setReviewDetailJson(String reviewDetailJson) { this.reviewDetailJson = reviewDetailJson; }
    }
}
