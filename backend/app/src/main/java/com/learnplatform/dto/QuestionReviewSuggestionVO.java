package com.learnplatform.dto;

import java.util.List;

/**
 * 正式题目 AI 复审建议 VO
 */
public class QuestionReviewSuggestionVO {

    /** 建议复审动作：APPROVE / REVISE / REJECT */
    private String recommendation;

    /** 0-100 的复审置信分 */
    private Integer confidenceScore;

    /** 一句话总评 */
    private String summary;

    /** 建议采用的题干，通常仅 REVISE 时返回 */
    private String suggestedContent;

    /** 建议难度，1-5 */
    private Integer suggestedDifficulty;

    /** 主要风险点 */
    private List<String> riskPoints;

    /** 修订建议 */
    private List<String> suggestions;

    /** 答案与解析检查说明 */
    private String answerAnalysis;

    /** 知识点与时效性检查说明 */
    private String knowledgeAnalysis;

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public Integer getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Integer confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getSuggestedContent() { return suggestedContent; }
    public void setSuggestedContent(String suggestedContent) { this.suggestedContent = suggestedContent; }

    public Integer getSuggestedDifficulty() { return suggestedDifficulty; }
    public void setSuggestedDifficulty(Integer suggestedDifficulty) { this.suggestedDifficulty = suggestedDifficulty; }

    public List<String> getRiskPoints() { return riskPoints; }
    public void setRiskPoints(List<String> riskPoints) { this.riskPoints = riskPoints; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }

    public String getAnswerAnalysis() { return answerAnalysis; }
    public void setAnswerAnalysis(String answerAnalysis) { this.answerAnalysis = answerAnalysis; }

    public String getKnowledgeAnalysis() { return knowledgeAnalysis; }
    public void setKnowledgeAnalysis(String knowledgeAnalysis) { this.knowledgeAnalysis = knowledgeAnalysis; }
}
