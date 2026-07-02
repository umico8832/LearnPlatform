package com.learnplatform.dto;

import java.util.List;

/**
 * 疑似重复题目分组 VO。
 */
public class QuestionDuplicateGroupVO {
    private String matchType;
    private Integer similarityScore;
    private String representativeContent;
    private List<QuestionVO> questions;

    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }
    public Integer getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(Integer similarityScore) { this.similarityScore = similarityScore; }
    public String getRepresentativeContent() { return representativeContent; }
    public void setRepresentativeContent(String representativeContent) { this.representativeContent = representativeContent; }
    public List<QuestionVO> getQuestions() { return questions; }
    public void setQuestions(List<QuestionVO> questions) { this.questions = questions; }
}
