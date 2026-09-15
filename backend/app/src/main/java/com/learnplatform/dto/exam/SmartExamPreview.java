package com.learnplatform.dto.exam;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** 智能组卷结果预览。 */
public class SmartExamPreview {

    @NotBlank(message = "试卷标题不能为空")
    @Size(max = 100, message = "试卷标题不能超过100个字符")
    private String title;
    private String description;
    @NotNull(message = "课程ID不能为空")
    private Long courseId;
    private String courseName;
    private Integer questionCount;
    private Integer totalScore;
    @NotNull(message = "考试时长不能为空")
    @Min(value = 1, message = "考试时长必须大于0")
    @Max(value = 1440, message = "考试时长不能超过1440分钟")
    private Integer duration;
    private Map<String, Integer> knowledgePointDistribution;
    private Map<String, Integer> difficultyDistribution;
    @NotEmpty(message = "试卷至少需要一道题")
    @Size(max = 100, message = "试卷题目不能超过100道")
    private List<@NotNull(message = "题目ID不能为空") Long> questionIds;
    private String recommendation;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Map<String, Integer> getKnowledgePointDistribution() {
        return knowledgePointDistribution;
    }

    public void setKnowledgePointDistribution(Map<String, Integer> knowledgePointDistribution) {
        this.knowledgePointDistribution = knowledgePointDistribution;
    }

    public Map<String, Integer> getDifficultyDistribution() {
        return difficultyDistribution;
    }

    public void setDifficultyDistribution(Map<String, Integer> difficultyDistribution) {
        this.difficultyDistribution = difficultyDistribution;
    }

    public List<Long> getQuestionIds() {
        return questionIds;
    }

    public void setQuestionIds(List<Long> questionIds) {
        this.questionIds = questionIds;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }
}
