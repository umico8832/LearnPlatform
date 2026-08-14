package com.learnplatform.dto;

import java.time.LocalDateTime;
import java.util.List;

public class CourseStageAssessmentVO {
    private Long id;
    private Long courseId;
    private String status;
    private String selectionStrategy;
    private Integer questionCount;
    private Integer correctCount;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    private CourseStageAssessmentSourceCompositionVO sourceComposition;
    private List<QuestionItem> questions;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSelectionStrategy() { return selectionStrategy; }
    public void setSelectionStrategy(String selectionStrategy) { this.selectionStrategy = selectionStrategy; }
    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }
    public CourseStageAssessmentSourceCompositionVO getSourceComposition() { return sourceComposition; }
    public void setSourceComposition(CourseStageAssessmentSourceCompositionVO value) { this.sourceComposition = value; }
    public List<QuestionItem> getQuestions() { return questions; }
    public void setQuestions(List<QuestionItem> questions) { this.questions = questions; }

    public static class QuestionItem {
        private Long id;
        private Long questionId;
        private Integer sortOrder;
        private String questionType;
        private String sourceType;
        private String sourceCategory;
        private Long originQuestionId;
        private String content;
        private List<OptionItem> options;
        private Integer score;
        private String userAnswer;
        private Boolean correct;
        private String correctAnswer;
        private String analysis;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getSourceCategory() { return sourceCategory; }
        public void setSourceCategory(String sourceCategory) { this.sourceCategory = sourceCategory; }
        public Long getOriginQuestionId() { return originQuestionId; }
        public void setOriginQuestionId(Long originQuestionId) { this.originQuestionId = originQuestionId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<OptionItem> getOptions() { return options; }
        public void setOptions(List<OptionItem> options) { this.options = options; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public String getUserAnswer() { return userAnswer; }
        public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
        public Boolean getCorrect() { return correct; }
        public void setCorrect(Boolean correct) { this.correct = correct; }
        public String getCorrectAnswer() { return correctAnswer; }
        public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
        public String getAnalysis() { return analysis; }
        public void setAnalysis(String analysis) { this.analysis = analysis; }
    }

    public static class OptionItem {
        private String label;
        private String content;

        public OptionItem() { }
        public OptionItem(String label, String content) { this.label = label; this.content = content; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
