package com.learnplatform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CourseStageAssessmentSubmitRequest {
    @NotEmpty(message = "必须提交完整测评答案")
    @Valid
    private List<Answer> answers;

    public List<Answer> getAnswers() { return answers; }
    public void setAnswers(List<Answer> answers) { this.answers = answers; }

    public static class Answer {
        @NotNull(message = "测评题目ID不能为空")
        private Long assessmentQuestionId;
        @NotBlank(message = "测评答案不能为空")
        @Size(max = 500, message = "测评答案过长")
        private String userAnswer;

        public Long getAssessmentQuestionId() { return assessmentQuestionId; }
        public void setAssessmentQuestionId(Long assessmentQuestionId) {
            this.assessmentQuestionId = assessmentQuestionId;
        }
        public String getUserAnswer() { return userAnswer; }
        public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    }
}
