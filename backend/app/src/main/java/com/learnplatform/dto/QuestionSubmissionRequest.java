package com.learnplatform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 题目投稿请求
 */
public class QuestionSubmissionRequest {

    @NotBlank(message = "题干内容不能为空")
    @Size(max = 10000, message = "题干内容不能超过10000字")
    private String content;

    @NotBlank(message = "题型不能为空")
    private String questionType;

    @NotNull(message = "所属课程不能为空")
    private Long courseId;

    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度最小为1")
    @Max(value = 5, message = "难度最大为5")
    private Integer difficulty;

    @Size(max = 10000, message = "解析不能超过10000字")
    private String analysis;

    /** 选项JSON（选择题/判断题） */
    private String optionsJson;

    /** 正确答案（填空/简答） */
    @Size(max = 2000, message = "正确答案不能超过2000字")
    private String correctAnswer;

    /** 关联知识点ID，逗号分隔 */
    private String knowledgePointIds;

    @Size(max = 500, message = "标签不能超过500字")
    private String tags;

    @Size(max = 200, message = "来源不能超过200字")
    private String source;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getKnowledgePointIds() { return knowledgePointIds; }
    public void setKnowledgePointIds(String knowledgePointIds) { this.knowledgePointIds = knowledgePointIds; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}