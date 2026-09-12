package com.learnplatform.dto;

import java.util.List;

/**
 * 学习路径推荐 VO
 */
public class LearningPathVO {

    /** 课程名称 */
    private String courseName;

    /** 用户总体掌握率（百分制） */
    private double overallMastery;

    /** 总知识点数 */
    private int totalKnowledgePoints;

    /** 已掌握知识点数（正确率 >= 70%） */
    private int masteredCount;

    /** 需要加强知识点数 */
    private int weakCount;

    /** 学习路径步骤列表（按优先级排序） */
    private List<PathStep> steps;

    /** 各课程掌握概况 */
    private List<CourseOverview> courseOverviews;

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public double getOverallMastery() { return overallMastery; }
    public void setOverallMastery(double overallMastery) { this.overallMastery = overallMastery; }
    public int getTotalKnowledgePoints() { return totalKnowledgePoints; }
    public void setTotalKnowledgePoints(int totalKnowledgePoints) { this.totalKnowledgePoints = totalKnowledgePoints; }
    public int getMasteredCount() { return masteredCount; }
    public void setMasteredCount(int masteredCount) { this.masteredCount = masteredCount; }
    public int getWeakCount() { return weakCount; }
    public void setWeakCount(int weakCount) { this.weakCount = weakCount; }
    public List<PathStep> getSteps() { return steps; }
    public void setSteps(List<PathStep> steps) { this.steps = steps; }
    public List<CourseOverview> getCourseOverviews() { return courseOverviews; }
    public void setCourseOverviews(List<CourseOverview> courseOverviews) { this.courseOverviews = courseOverviews; }

    /**
     * 学习路径单步
     */
    public static class PathStep {
        /** 排序序号 */
        private int order;
        /** 知识点 ID */
        private Long knowledgePointId;
        /** 知识点名称 */
        private String knowledgePointName;
        /** 所属课程 ID */
        private Long courseId;
        /** 所属课程名称 */
        private String courseName;
        /** 父知识点 ID */
        private Long parentId;
        /** 当前正确率（百分制，-1 表示未练习过） */
        private double correctRate;
        /** 总练习题数 */
        private int totalAttempts;
        /** 错题数 */
        private int wrongCount;
        /** 掌握程度：MASTERED / NEEDS_REVIEW / WEAK / NOT_STARTED */
        private String masteryStatus;
        /** 优先级得分（越高越需要学习） */
        private double priorityScore;
        /** AI 推荐的下一步行动 */
        private String recommendation;

        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }
        public Long getKnowledgePointId() { return knowledgePointId; }
        public void setKnowledgePointId(Long knowledgePointId) { this.knowledgePointId = knowledgePointId; }
        public String getKnowledgePointName() { return knowledgePointName; }
        public void setKnowledgePointName(String knowledgePointName) { this.knowledgePointName = knowledgePointName; }
        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public double getCorrectRate() { return correctRate; }
        public void setCorrectRate(double correctRate) { this.correctRate = correctRate; }
        public int getTotalAttempts() { return totalAttempts; }
        public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }
        public int getWrongCount() { return wrongCount; }
        public void setWrongCount(int wrongCount) { this.wrongCount = wrongCount; }
        public String getMasteryStatus() { return masteryStatus; }
        public void setMasteryStatus(String masteryStatus) { this.masteryStatus = masteryStatus; }
        public double getPriorityScore() { return priorityScore; }
        public void setPriorityScore(double priorityScore) { this.priorityScore = priorityScore; }
        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    }

    /**
     * 课程掌握概况
     */
    public static class CourseOverview {
        private Long courseId;
        private String courseName;
        private double correctRate;
        private int totalAttempts;
        private int knowledgePointCount;
        private int masteredPointCount;

        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public double getCorrectRate() { return correctRate; }
        public void setCorrectRate(double correctRate) { this.correctRate = correctRate; }
        public int getTotalAttempts() { return totalAttempts; }
        public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }
        public int getKnowledgePointCount() { return knowledgePointCount; }
        public void setKnowledgePointCount(int knowledgePointCount) { this.knowledgePointCount = knowledgePointCount; }
        public int getMasteredPointCount() { return masteredPointCount; }
        public void setMasteredPointCount(int masteredPointCount) { this.masteredPointCount = masteredPointCount; }
    }
}