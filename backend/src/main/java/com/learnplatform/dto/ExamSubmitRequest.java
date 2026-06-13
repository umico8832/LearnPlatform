package com.learnplatform.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 提交考试请求
 */
public class ExamSubmitRequest {
    @NotNull(message = "考试记录ID不能为空")
    private Long examRecordId;
    @NotEmpty(message = "答案列表不能为空")
    @Valid
    private List<AnswerItem> answers;

    public Long getExamRecordId() { return examRecordId; }
    public void setExamRecordId(Long examRecordId) { this.examRecordId = examRecordId; }
    public List<AnswerItem> getAnswers() { return answers; }
    public void setAnswers(List<AnswerItem> answers) { this.answers = answers; }

    public static class AnswerItem {
        @NotNull(message = "题目ID不能为空")
        private Long questionId;
        private String userAnswer;

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public String getUserAnswer() { return userAnswer; }
        public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    }
}
