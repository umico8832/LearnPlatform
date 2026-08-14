package com.learnplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 试卷学习会话中一次可追溯的 AI 辅导调用。 */
@TableName("exam_learning_ai_interaction")
public class ExamLearningAiInteraction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long courseId;
    private Long examPaperId;
    private Long learningSessionId;
    private Long questionId;
    private Long answerId;
    private Integer answerAttemptNo;
    private Integer answerCorrect;
    private String interactionType;
    private Integer status;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getExamPaperId() { return examPaperId; }
    public void setExamPaperId(Long examPaperId) { this.examPaperId = examPaperId; }
    public Long getLearningSessionId() { return learningSessionId; }
    public void setLearningSessionId(Long learningSessionId) { this.learningSessionId = learningSessionId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Long getAnswerId() { return answerId; }
    public void setAnswerId(Long answerId) { this.answerId = answerId; }
    public Integer getAnswerAttemptNo() { return answerAttemptNo; }
    public void setAnswerAttemptNo(Integer answerAttemptNo) { this.answerAttemptNo = answerAttemptNo; }
    public Integer getAnswerCorrect() { return answerCorrect; }
    public void setAnswerCorrect(Integer answerCorrect) { this.answerCorrect = answerCorrect; }
    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
