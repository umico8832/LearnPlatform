package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tutor_content")
public class TutorContent {
    @TableId private Long id;
    private Long knowledgePointId;
    private String contentKey;
    private Integer contentVersion;
    private String reviewStatus;
    private String title;
    private String lessonJson;
    private String checkJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKnowledgePointId() { return knowledgePointId; }
    public void setKnowledgePointId(Long value) { knowledgePointId = value; }
    public String getContentKey() { return contentKey; }
    public void setContentKey(String value) { contentKey = value; }
    public Integer getContentVersion() { return contentVersion; }
    public void setContentVersion(Integer value) { contentVersion = value; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String value) { reviewStatus = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getLessonJson() { return lessonJson; }
    public void setLessonJson(String value) { lessonJson = value; }
    public String getCheckJson() { return checkJson; }
    public void setCheckJson(String value) { checkJson = value; }
}
