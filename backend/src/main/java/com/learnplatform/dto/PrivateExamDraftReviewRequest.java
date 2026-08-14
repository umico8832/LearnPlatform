package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class PrivateExamDraftReviewRequest {
    @NotEmpty(message = "复核答案不能为空")
    @Size(max = 6, message = "复核答案选项不能超过6个")
    private List<@NotBlank(message = "答案标签不能为空") String> answerLabels;
    @NotBlank(message = "复核解析不能为空")
    @Size(max = 10000, message = "复核解析不能超过10000个字符")
    private String analysis;

    public List<String> getAnswerLabels() { return answerLabels; }
    public void setAnswerLabels(List<String> answerLabels) { this.answerLabels = answerLabels; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
}
