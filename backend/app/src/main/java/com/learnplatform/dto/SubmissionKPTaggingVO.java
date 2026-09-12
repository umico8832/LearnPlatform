package com.learnplatform.dto;

import java.util.List;

/**
 * 投稿 AI 知识点标注结果 VO
 */
public class SubmissionKPTaggingVO {

    /** 推荐的知识点列表 */
    private List<TaggedKP> recommendations;

    /** AI 分析说明 */
    private String analysis;

    /** 推荐的知识点 ID 列表（逗号分隔），便于前端一键应用 */
    private String suggestedIds;

    public SubmissionKPTaggingVO() {}

    public SubmissionKPTaggingVO(List<TaggedKP> recommendations, String analysis, String suggestedIds) {
        this.recommendations = recommendations;
        this.analysis = analysis;
        this.suggestedIds = suggestedIds;
    }

    public List<TaggedKP> getRecommendations() { return recommendations; }
    public void setRecommendations(List<TaggedKP> recommendations) { this.recommendations = recommendations; }

    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }

    public String getSuggestedIds() { return suggestedIds; }
    public void setSuggestedIds(String suggestedIds) { this.suggestedIds = suggestedIds; }

    /**
     * 单个推荐知识点
     */
    public static class TaggedKP {
        /** 知识点 ID */
        private Long id;
        /** 知识点名称 */
        private String name;
        /** 所属课程名称（可选） */
        private String courseName;
        /** 置信度：HIGH / MEDIUM / LOW */
        private String confidence;
        /** 推荐理由 */
        private String reason;

        public TaggedKP() {}

        public TaggedKP(Long id, String name, String courseName, String confidence, String reason) {
            this.id = id;
            this.name = name;
            this.courseName = courseName;
            this.confidence = confidence;
            this.reason = reason;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }

        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}