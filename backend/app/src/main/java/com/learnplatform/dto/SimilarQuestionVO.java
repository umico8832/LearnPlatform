package com.learnplatform.dto;

import java.util.List;

/**
 * 相似题推荐 VO
 *
 * 根据用户答错的题目，推荐同知识点、同题型或同难度的相似题目。
 */
public class SimilarQuestionVO {

    /** 源题目 ID */
    private Long sourceQuestionId;
    /** 源题目内容（截断） */
    private String sourceQuestionContent;
    /** 推荐的相似题目列表 */
    private List<SimilarItem> similarQuestions;

    public Long getSourceQuestionId() { return sourceQuestionId; }
    public void setSourceQuestionId(Long sourceQuestionId) { this.sourceQuestionId = sourceQuestionId; }
    public String getSourceQuestionContent() { return sourceQuestionContent; }
    public void setSourceQuestionContent(String sourceQuestionContent) {
        this.sourceQuestionContent = sourceQuestionContent;
    }
    public List<SimilarItem> getSimilarQuestions() { return similarQuestions; }
    public void setSimilarQuestions(List<SimilarItem> similarQuestions) { this.similarQuestions = similarQuestions; }

    /**
     * 单个相似题目
     */
    public static class SimilarItem {
        private Long questionId;
        private String questionContent;
        private String questionType;
        private Integer difficulty;
        private String courseName;
        private String knowledgePointName;
        /** 相似度得分（0-100） */
        private int similarityScore;
        /** 相似原因说明 */
        private String reason;
        /** 用户是否已练习过该题 */
        private boolean alreadyAttempted;

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public String getQuestionContent() { return questionContent; }
        public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        public Integer getDifficulty() { return difficulty; }
        public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public String getKnowledgePointName() { return knowledgePointName; }
        public void setKnowledgePointName(String knowledgePointName) { this.knowledgePointName = knowledgePointName; }
        public int getSimilarityScore() { return similarityScore; }
        public void setSimilarityScore(int similarityScore) { this.similarityScore = similarityScore; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public boolean isAlreadyAttempted() { return alreadyAttempted; }
        public void setAlreadyAttempted(boolean alreadyAttempted) { this.alreadyAttempted = alreadyAttempted; }
    }
}