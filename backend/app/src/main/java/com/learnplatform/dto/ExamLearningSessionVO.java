package com.learnplatform.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ExamLearningSessionVO {
    private Long id;
    private Long examPaperId;
    private String paperTitle;
    private Long courseId;
    private String paperType;
    private String examName;
    private Integer examYear;
    private String sourceReference;
    private Boolean sourceVerified;
    private Integer status;
    private Long currentQuestionId;
    private Integer answeredQuestionCount;
    private Integer correctQuestionCount;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    private List<QuestionItem> questions;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExamPaperId() { return examPaperId; }
    public void setExamPaperId(Long examPaperId) { this.examPaperId = examPaperId; }
    public String getPaperTitle() { return paperTitle; }
    public void setPaperTitle(String paperTitle) { this.paperTitle = paperTitle; }
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
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getCurrentQuestionId() { return currentQuestionId; }
    public void setCurrentQuestionId(Long currentQuestionId) { this.currentQuestionId = currentQuestionId; }
    public Integer getAnsweredQuestionCount() { return answeredQuestionCount; }
    public void setAnsweredQuestionCount(Integer value) { answeredQuestionCount = value; }
    public Integer getCorrectQuestionCount() { return correctQuestionCount; }
    public void setCorrectQuestionCount(Integer value) { correctQuestionCount = value; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }
    public List<QuestionItem> getQuestions() { return questions; }
    public void setQuestions(List<QuestionItem> questions) { this.questions = questions; }

    public static class QuestionItem {
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
        private ExamLearningAnswerResultVO latestAnswer;

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
        public void setMajorQuestionNumber(String value) { majorQuestionNumber = value; }
        public String getMinorQuestionNumber() { return minorQuestionNumber; }
        public void setMinorQuestionNumber(String value) { minorQuestionNumber = value; }
        public String getSubquestionNumber() { return subquestionNumber; }
        public void setSubquestionNumber(String value) { subquestionNumber = value; }
        public String getDisplayNumber() { return displayNumber; }
        public void setDisplayNumber(String displayNumber) { this.displayNumber = displayNumber; }
        public List<QuestionOptionVO> getOptions() { return options; }
        public void setOptions(List<QuestionOptionVO> options) { this.options = options; }
        public ExamLearningAnswerResultVO getLatestAnswer() { return latestAnswer; }
        public void setLatestAnswer(ExamLearningAnswerResultVO value) { latestAnswer = value; }
    }
}
