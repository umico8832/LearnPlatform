package com.learnplatform.dto.exam;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 智能组卷请求参数。 */
public class SmartExamRequest {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;
    @NotNull(message = "题目数量不能为空")
    @Min(value = 1, message = "题目数量应在 1-100 之间")
    @Max(value = 100, message = "题目数量应在 1-100 之间")
    private Integer questionCount = 20;
    @NotNull(message = "难度模式不能为空")
    @Pattern(regexp = "EASY|BALANCED|HARD|ADAPTIVE", message = "不支持的难度模式")
    private String difficultyMode = "ADAPTIVE";
    private boolean includeWrongQuestions = true;
    @Size(max = 100, message = "试卷标题不能超过100个字符")
    private String title;
    @NotNull(message = "考试时长不能为空")
    @Min(value = 1, message = "考试时长必须大于0")
    @Max(value = 1440, message = "考试时长不能超过1440分钟")
    private Integer duration = 60;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public String getDifficultyMode() {
        return difficultyMode;
    }

    public void setDifficultyMode(String difficultyMode) {
        this.difficultyMode = difficultyMode;
    }

    public boolean isIncludeWrongQuestions() {
        return includeWrongQuestions;
    }

    public void setIncludeWrongQuestions(boolean includeWrongQuestions) {
        this.includeWrongQuestions = includeWrongQuestions;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }
}
