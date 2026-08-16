package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tutor_session")
public class TutorSession {
    @TableId private Long id;
    private String sessionKey;
    private Long userId;
    private Long courseId;
    private Long knowledgePointId;
    private Long tutorContentId;
    private String learningContextJson;
    private String checkAnswer;
    private Boolean checkCorrect;
    private LocalDateTime checkAnsweredAt;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String value) { sessionKey = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long value) { courseId = value; }
    public Long getKnowledgePointId() { return knowledgePointId; }
    public void setKnowledgePointId(Long value) { knowledgePointId = value; }
    public Long getTutorContentId() { return tutorContentId; }
    public void setTutorContentId(Long value) { tutorContentId = value; }
    public String getLearningContextJson() { return learningContextJson; }
    public void setLearningContextJson(String value) { learningContextJson = value; }
    public String getCheckAnswer() { return checkAnswer; }
    public void setCheckAnswer(String value) { checkAnswer = value; }
    public Boolean getCheckCorrect() { return checkCorrect; }
    public void setCheckCorrect(Boolean value) { checkCorrect = value; }
    public LocalDateTime getCheckAnsweredAt() { return checkAnsweredAt; }
    public void setCheckAnsweredAt(LocalDateTime value) { checkAnsweredAt = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime = value; }
}
