package com.learnplatform.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 当前用户在一门已加入课程中的可解释学习概况。 */
public class CourseOverviewVO {

    private Long courseId;
    private String courseName;
    private int answeredCount;
    private int correctCount;
    private int dueReviewCount;
    private int unresolvedWrongCount;
    private LocalDateTime lastLearningTime;
    private List<LearningTargetVO> recommendedTargets;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public int getAnsweredCount() { return answeredCount; }
    public void setAnsweredCount(int answeredCount) { this.answeredCount = answeredCount; }
    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }
    public int getDueReviewCount() { return dueReviewCount; }
    public void setDueReviewCount(int dueReviewCount) { this.dueReviewCount = dueReviewCount; }
    public int getUnresolvedWrongCount() { return unresolvedWrongCount; }
    public void setUnresolvedWrongCount(int unresolvedWrongCount) { this.unresolvedWrongCount = unresolvedWrongCount; }
    public LocalDateTime getLastLearningTime() { return lastLearningTime; }
    public void setLastLearningTime(LocalDateTime lastLearningTime) { this.lastLearningTime = lastLearningTime; }
    public List<LearningTargetVO> getRecommendedTargets() { return recommendedTargets; }
    public void setRecommendedTargets(List<LearningTargetVO> recommendedTargets) { this.recommendedTargets = recommendedTargets; }

    public static class LearningTargetVO {
        private String type;
        private String title;
        private String reason;
        private Long questionId;
        private Long knowledgePointId;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public Long getKnowledgePointId() { return knowledgePointId; }
        public void setKnowledgePointId(Long knowledgePointId) { this.knowledgePointId = knowledgePointId; }
    }
}
