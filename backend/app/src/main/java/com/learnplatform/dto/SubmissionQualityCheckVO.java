package com.learnplatform.dto;

import java.util.List;

/**
 * 投稿 AI 质检结果 VO
 */
public class SubmissionQualityCheckVO {

    /** 综合质量评分 0-100 */
    private Integer qualityScore;

    /** 总体评价 */
    private String summary;

    /** 推荐审核意见：APPROVE / REVISE / REJECT */
    private String recommendation;

    /** 格式规范检查结果 */
    private CheckItem formatCheck;

    /** 内容完整性检查结果 */
    private CheckItem completenessCheck;

    /** 答案正确性检查结果 */
    private CheckItem answerCheck;

    /** 解析质量检查结果 */
    private CheckItem analysisCheck;

    /** 知识点相关性检查结果 */
    private CheckItem knowledgePointCheck;

    /** 风险点列表 */
    private List<String> riskPoints;

    /** 修改建议列表 */
    private List<String> suggestions;

    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public CheckItem getFormatCheck() { return formatCheck; }
    public void setFormatCheck(CheckItem formatCheck) { this.formatCheck = formatCheck; }

    public CheckItem getCompletenessCheck() { return completenessCheck; }
    public void setCompletenessCheck(CheckItem completenessCheck) { this.completenessCheck = completenessCheck; }

    public CheckItem getAnswerCheck() { return answerCheck; }
    public void setAnswerCheck(CheckItem answerCheck) { this.answerCheck = answerCheck; }

    public CheckItem getAnalysisCheck() { return analysisCheck; }
    public void setAnalysisCheck(CheckItem analysisCheck) { this.analysisCheck = analysisCheck; }

    public CheckItem getKnowledgePointCheck() { return knowledgePointCheck; }
    public void setKnowledgePointCheck(CheckItem knowledgePointCheck) {
        this.knowledgePointCheck = knowledgePointCheck;
    }

    public List<String> getRiskPoints() { return riskPoints; }
    public void setRiskPoints(List<String> riskPoints) { this.riskPoints = riskPoints; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }

    /**
     * 单项检查结果
     */
    public static class CheckItem {
        /** 状态：PASS / WARNING / FAIL */
        private String status;
        /** 检查说明 */
        private String detail;

        public CheckItem() {}

        public CheckItem(String status, String detail) {
            this.status = status;
            this.detail = detail;
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
    }
}