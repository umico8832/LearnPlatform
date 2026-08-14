package com.learnplatform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PrivateExamPdfRequest {
    @NotBlank(message = "试卷标题不能为空")
    @Size(max = 200, message = "试卷标题不能超过200个字符")
    private String title;
    @NotNull(message = "课程ID不能为空")
    private Long courseId;
    @Min(value = 1, message = "考试时长至少为1分钟")
    @Max(value = 600, message = "考试时长不能超过600分钟")
    private Integer duration;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
}
