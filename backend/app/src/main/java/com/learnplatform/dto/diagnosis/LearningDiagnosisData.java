package com.learnplatform.dto.diagnosis;

import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.WrongQuestion;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 一次学习诊断计算共享的基础事实快照。 */
public record LearningDiagnosisData(
        List<PracticeRecord> records,
        List<WrongQuestion> wrongs,
        List<KnowledgePoint> knowledgePoints,
        Map<Long, Set<Long>> questionToKnowledgePoints) {
}
