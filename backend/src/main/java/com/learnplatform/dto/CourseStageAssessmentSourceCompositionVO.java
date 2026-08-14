package com.learnplatform.dto;

/** 阶段测评创建时固化的题目来源构成，不表达质量、难度或掌握度。 */
public class CourseStageAssessmentSourceCompositionVO {
    private int officialExamCount;
    private int manualCount;
    private int userPrivateCount;
    private int aiGeneratedCount;

    public int getOfficialExamCount() { return officialExamCount; }
    public void setOfficialExamCount(int value) { this.officialExamCount = value; }
    public int getManualCount() { return manualCount; }
    public void setManualCount(int value) { this.manualCount = value; }
    public int getUserPrivateCount() { return userPrivateCount; }
    public void setUserPrivateCount(int value) { this.userPrivateCount = value; }
    public int getAiGeneratedCount() { return aiGeneratedCount; }
    public void setAiGeneratedCount(int value) { this.aiGeneratedCount = value; }
}
