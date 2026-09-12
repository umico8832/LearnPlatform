package com.learnplatform.dto;

/** 服务端判分后的结果；学习建议仅引用已审查内容中声明的受限路径信息。 */
public class TutorCheckResultVO {
    private boolean correct;
    private String explanation;
    private String guidanceType;
    private String guidanceTitle;
    private String guidanceDescription;
    private Long guidanceKnowledgePointId;

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean value) { correct = value; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String value) { explanation = value; }
    public String getGuidanceType() { return guidanceType; }
    public void setGuidanceType(String value) { guidanceType = value; }
    public String getGuidanceTitle() { return guidanceTitle; }
    public void setGuidanceTitle(String value) { guidanceTitle = value; }
    public String getGuidanceDescription() { return guidanceDescription; }
    public void setGuidanceDescription(String value) { guidanceDescription = value; }
    public Long getGuidanceKnowledgePointId() { return guidanceKnowledgePointId; }
    public void setGuidanceKnowledgePointId(Long value) { guidanceKnowledgePointId = value; }
}
