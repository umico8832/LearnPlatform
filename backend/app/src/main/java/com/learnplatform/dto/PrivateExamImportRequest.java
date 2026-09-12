package com.learnplatform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PrivateExamImportRequest {
    @NotBlank(message = "试卷标题不能为空")
    @Size(max = 200, message = "试卷标题不能超过200个字符")
    private String title;
    @NotNull(message = "课程ID不能为空")
    private Long courseId;
    @Min(value = 1, message = "考试时长至少为1分钟")
    @Max(value = 600, message = "考试时长不能超过600分钟")
    private Integer duration;
    @NotBlank(message = "原始资料名称不能为空")
    @Size(max = 255, message = "原始资料名称不能超过255个字符")
    private String sourceName;
    @NotBlank(message = "资料格式不能为空")
    @Pattern(regexp = "MARKDOWN|TEXT", message = "仅支持 MARKDOWN 或 TEXT")
    private String sourceFormat;
    @NotBlank(message = "原始资料内容不能为空")
    @Size(max = 100000, message = "原始资料内容不能超过100000个字符")
    private String content;

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
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
