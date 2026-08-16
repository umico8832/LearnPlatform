package com.learnplatform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.CourseStageAssessmentVO;
import com.learnplatform.entity.CourseStageAssessmentQuestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从测评逐题快照聚合知识点事实。与 {@link CourseStageAssessmentSourceComposition} 一样只读取
 * 创建时固化的快照，不读取当前题目知识点关联；只统计题数与正误数，不表达掌握度。
 */
final class CourseStageAssessmentKnowledgePointSummary {
    private CourseStageAssessmentKnowledgePointSummary() { }

    static List<CourseStageAssessmentVO.KnowledgePointSummaryVO> from(
            List<CourseStageAssessmentQuestion> items, ObjectMapper objectMapper) {
        Map<Key, int[]> counts = new LinkedHashMap<>();
        for (CourseStageAssessmentQuestion item : items) {
            for (CourseStageAssessmentVO.KnowledgePointVO point
                    : readKnowledgePoints(item.getKnowledgePointsJson(), objectMapper)) {
                int[] counters = counts.computeIfAbsent(new Key(point.getId(), point.getName()),
                        key -> new int[2]);
                counters[0]++;
                if (Integer.valueOf(1).equals(item.getIsCorrect())) { counters[1]++; }
            }
        }
        List<CourseStageAssessmentVO.KnowledgePointSummaryVO> summary = new ArrayList<>();
        for (Map.Entry<Key, int[]> entry : counts.entrySet()) {
            CourseStageAssessmentVO.KnowledgePointSummaryVO view =
                    new CourseStageAssessmentVO.KnowledgePointSummaryVO();
            view.setId(entry.getKey().id);
            view.setName(entry.getKey().name);
            view.setQuestionCount(entry.getValue()[0]);
            view.setCorrectCount(entry.getValue()[1]);
            summary.add(view);
        }
        return summary;
    }

    static List<CourseStageAssessmentVO.KnowledgePointVO> readKnowledgePoints(
            String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) { return List.of(); }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "测评题目知识点快照损坏");
        }
    }

    private record Key(Long id, String name) { }
}
