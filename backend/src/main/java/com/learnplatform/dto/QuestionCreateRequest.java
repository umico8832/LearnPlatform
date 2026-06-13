package com.learnplatform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 创建/更新题目请求 DTO
 */
public class QuestionCreateRequest {
    @NotBlank(message = "题干内容不能为空")
    private String content;

    @NotBlank(message = "题目类型不能为空")
    private String questionType;

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    private Integer difficulty;
    private String analysis;
    private String tags;
    private Integer score;
    private List<OptionItem> options;
    private List<Long> knowledgePointIds;

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
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public List<OptionItem> getOptions() { return options; }
    public void setOptions(List<OptionItem> options) { this.options = options; }
    public List<Long> getKnowledgePointIds() { return knowledgePointIds; }
    public void setKnowledgePointIds(List<Long> knowledgePointIds) { this.knowledgePointIds = knowledgePointIds; }

    /**
     * 选项项
     */
    public static class OptionItem {
        private String content;
        private String optionLabel;
        private Integer isCorrect;
        private Integer sortOrder;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getOptionLabel() { return optionLabel; }
        public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }
        public Integer getIsCorrect() { return isCorrect; }
        public void setIsCorrect(Integer isCorrect) { this.isCorrect = isCorrect; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}