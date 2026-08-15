package com.learnplatform.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 本人课程阶段测评历史中的已完成事实摘要。 */
public class CourseStageAssessmentSummaryVO {
    private Long id;
    private String selectionStrategy;
    private Long targetKnowledgePointId;
    private String targetKnowledgePointName;
    private Integer questionCount;
    private Integer correctCount;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    private CourseStageAssessmentSourceCompositionVO sourceComposition;
    private List<CourseStageAssessmentVO.KnowledgePointSummaryVO> knowledgePointSummary;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSelectionStrategy() { return selectionStrategy; }
    public void setSelectionStrategy(String selectionStrategy) { this.selectionStrategy = selectionStrategy; }
    public Long getTargetKnowledgePointId() { return targetKnowledgePointId; }
    public void setTargetKnowledgePointId(Long value) { this.targetKnowledgePointId = value; }
    public String getTargetKnowledgePointName() { return targetKnowledgePointName; }
    public void setTargetKnowledgePointName(String value) { this.targetKnowledgePointName = value; }
    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }
    public CourseStageAssessmentSourceCompositionVO getSourceComposition() { return sourceComposition; }
    public void setSourceComposition(CourseStageAssessmentSourceCompositionVO value) { this.sourceComposition = value; }
    public List<CourseStageAssessmentVO.KnowledgePointSummaryVO> getKnowledgePointSummary() { return knowledgePointSummary; }
    public void setKnowledgePointSummary(List<CourseStageAssessmentVO.KnowledgePointSummaryVO> value) { this.knowledgePointSummary = value; }
}
