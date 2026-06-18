package com.learnplatform.dto;

import java.util.List;

/**
 * 投稿 AI 难度评估结果 VO
 */
public class SubmissionDifficultyVO {

    /** AI 评估难度 1-5 */
    private Integer suggestedDifficulty;

    /** 用户原始标注难度 1-5 */
    private Integer originalDifficulty;

    /** AI 与用户标注是否一致 */
    private Boolean difficultyMatch;

    /** 置信度：HIGH / MEDIUM / LOW */
    private String confidence;

    /** 难度评估理由 */
    private String reason;

    /** 认知层次（布鲁姆分类法）：记忆/理解/应用/分析/评价/创建 */
    private String cognitiveLevel;

    /** 影响难度的因素列表 */
    private List<DifficultyFactor> factors;

    /** 总体评估说明 */
    private String summary;

    /**
     * 难度影响因素
     */
    public static class DifficultyFactor {
        /** 因素名称 */
        private String name;
        /** 因素描述 */
        private String description;
        /** 影响方向：INCREASE（增难）/ DECREASE（降难）/ NEUTRAL */
        private String impact;

        public DifficultyFactor() {}

        public DifficultyFactor(String name, String description, String impact) {
            this.name = name;
            this.description = description;
            this.impact = impact;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getImpact() { return impact; }
        public void setImpact(String impact) { this.impact = impact; }
    }

    public Integer getSuggestedDifficulty() { return suggestedDifficulty; }
    public void setSuggestedDifficulty(Integer suggestedDifficulty) { this.suggestedDifficulty = suggestedDifficulty; }

    public Integer getOriginalDifficulty() { return originalDifficulty; }
    public void setOriginalDifficulty(Integer originalDifficulty) { this.originalDifficulty = originalDifficulty; }

    public Boolean getDifficultyMatch() { return difficultyMatch; }
    public void setDifficultyMatch(Boolean difficultyMatch) { this.difficultyMatch = difficultyMatch; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getCognitiveLevel() { return cognitiveLevel; }
    public void setCognitiveLevel(String cognitiveLevel) { this.cognitiveLevel = cognitiveLevel; }

    public List<DifficultyFactor> getFactors() { return factors; }
    public void setFactors(List<DifficultyFactor> factors) { this.factors = factors; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}