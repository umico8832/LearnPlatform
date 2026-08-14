package com.learnplatform.dto;

import java.util.List;

public class PrivateExamImportPreviewVO {
    private String title;
    private Long courseId;
    private Integer duration;
    private String sourceName;
    private String sourceFormat;
    private String contentHash;
    private Integer questionCount;
    private Integer totalScore;
    private Boolean requiresAnswerReview;
    private List<QuestionPreview> questions;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceFormat() { return sourceFormat; }
    public void setSourceFormat(String sourceFormat) { this.sourceFormat = sourceFormat; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public Boolean getRequiresAnswerReview() { return requiresAnswerReview; }
    public void setRequiresAnswerReview(Boolean requiresAnswerReview) { this.requiresAnswerReview = requiresAnswerReview; }
    public List<QuestionPreview> getQuestions() { return questions; }
    public void setQuestions(List<QuestionPreview> questions) { this.questions = questions; }

    public static class QuestionPreview {
        private String content;
        private String questionType;
        private String answer;
        private String analysis;
        private Integer score;
        private Boolean answerComplete;
        private List<OptionPreview> options;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
        public String getAnalysis() { return analysis; }
        public void setAnalysis(String analysis) { this.analysis = analysis; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public Boolean getAnswerComplete() { return answerComplete; }
        public void setAnswerComplete(Boolean answerComplete) { this.answerComplete = answerComplete; }
        public List<OptionPreview> getOptions() { return options; }
        public void setOptions(List<OptionPreview> options) { this.options = options; }
    }

    public static class OptionPreview {
        private String label;
        private String content;
        private Boolean correct;

        public OptionPreview() { }
        public OptionPreview(String label, String content, Boolean correct) {
            this.label = label;
            this.content = content;
            this.correct = correct;
        }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Boolean getCorrect() { return correct; }
        public void setCorrect(Boolean correct) { this.correct = correct; }
    }
}
