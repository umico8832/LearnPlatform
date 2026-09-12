package com.learnplatform.dto;

import java.time.LocalDateTime;

/** Tutor 会话启动时消费的课程学习证据聚合快照，不包含原始答案或 AI 输出。 */
public class TutorLearningContextVO {
    private int paperAnswerCount;
    private int paperIncorrectCount;
    private int paperAiAssistanceCount;
    private int unresolvedWrongCount;
    private int dueReviewCount;
    private int reviewAnswerCount;
    private LocalDateTime latestEvidenceAt;

    public int getPaperAnswerCount() { return paperAnswerCount; }
    public void setPaperAnswerCount(int paperAnswerCount) { this.paperAnswerCount = paperAnswerCount; }
    public int getPaperIncorrectCount() { return paperIncorrectCount; }
    public void setPaperIncorrectCount(int paperIncorrectCount) { this.paperIncorrectCount = paperIncorrectCount; }
    public int getPaperAiAssistanceCount() { return paperAiAssistanceCount; }
    public void setPaperAiAssistanceCount(int paperAiAssistanceCount) {
        this.paperAiAssistanceCount = paperAiAssistanceCount;
    }
    public int getUnresolvedWrongCount() { return unresolvedWrongCount; }
    public void setUnresolvedWrongCount(int unresolvedWrongCount) { this.unresolvedWrongCount = unresolvedWrongCount; }
    public int getDueReviewCount() { return dueReviewCount; }
    public void setDueReviewCount(int dueReviewCount) { this.dueReviewCount = dueReviewCount; }
    public int getReviewAnswerCount() { return reviewAnswerCount; }
    public void setReviewAnswerCount(int reviewAnswerCount) { this.reviewAnswerCount = reviewAnswerCount; }
    public LocalDateTime getLatestEvidenceAt() { return latestEvidenceAt; }
    public void setLatestEvidenceAt(LocalDateTime latestEvidenceAt) { this.latestEvidenceAt = latestEvidenceAt; }
}
