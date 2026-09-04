package com.learnplatform.service.exam;

import com.learnplatform.entity.Question;
import com.learnplatform.service.AiExamGenerationService.SmartExamPreview;
import com.learnplatform.service.AiExamGenerationService.SmartExamRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 将已选题目转换为保持原接口字段语义的智能组卷预览。 */
@Service
public class AiExamPreviewPresentationService {

    private static final Map<Integer, String> DIFFICULTY_LABELS = Map.of(
            1, "★", 2, "★★", 3, "★★★", 4, "★★★★", 5, "★★★★★");

    public SmartExamPreview create(SmartExamRequest request,
                                   List<Question> availableQuestions,
                                   List<Long> selectedIds,
                                   Map<Long, List<Long>> questionKnowledgePoints,
                                   Map<Long, String> knowledgePointNames,
                                   Set<Long> wrongQuestionIds,
                                   Map<Integer, Double> difficultyAccuracy) {
        SmartExamPreview preview = new SmartExamPreview();
        preview.setTitle(request.getTitle() != null ? request.getTitle() : generateTitle(request));
        preview.setDescription(generateDescription(request, selectedIds, questionKnowledgePoints));
        preview.setCourseId(request.getCourseId());
        preview.setQuestionCount(selectedIds.size());
        preview.setTotalScore(selectedIds.size());
        preview.setDuration(request.getDuration() != null ? request.getDuration() : 60);
        preview.setQuestionIds(selectedIds);
        preview.setKnowledgePointDistribution(
                knowledgePointDistribution(selectedIds, questionKnowledgePoints, knowledgePointNames));
        preview.setDifficultyDistribution(difficultyDistribution(selectedIds, availableQuestions));
        preview.setRecommendation(buildRecommendation(request, selectedIds, wrongQuestionIds, difficultyAccuracy));
        return preview;
    }

    private Map<String, Integer> knowledgePointDistribution(List<Long> selectedIds,
                                                             Map<Long, List<Long>> questionKnowledgePoints,
                                                             Map<Long, String> knowledgePointNames) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (Long questionId : selectedIds) {
            for (Long knowledgePointId : questionKnowledgePoints.getOrDefault(questionId, Collections.emptyList())) {
                distribution.merge(knowledgePointNames.getOrDefault(knowledgePointId, "未知知识点"),
                        1, Integer::sum);
            }
        }
        return distribution;
    }

    private Map<String, Integer> difficultyDistribution(List<Long> selectedIds, List<Question> questions) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (Long questionId : selectedIds) {
            Question question = questions.stream().filter(item -> item.getId().equals(questionId))
                    .findFirst().orElse(null);
            if (question != null && question.getDifficulty() != null) {
                distribution.merge(DIFFICULTY_LABELS.getOrDefault(question.getDifficulty(), "未知"), 1, Integer::sum);
            }
        }
        return distribution;
    }

    private String generateTitle(SmartExamRequest request) {
        return "智能模拟试卷（" + request.getDifficultyMode() + "）";
    }

    private String generateDescription(SmartExamRequest request,
                                       List<Long> selectedIds,
                                       Map<Long, List<Long>> questionKnowledgePoints) {
        Set<Long> coveredKnowledgePoints = new HashSet<>();
        for (Long questionId : selectedIds) {
            coveredKnowledgePoints.addAll(questionKnowledgePoints.getOrDefault(questionId, Collections.emptyList()));
        }
        return "由 AI 智能组卷系统自动生成。题目数量：" + selectedIds.size()
                + " 道。覆盖知识点："
                + coveredKnowledgePoints.size() + " 个。"
                + (request.isIncludeWrongQuestions() ? "已包含用户易错题目。" : "")
                + "难度模式：" + difficultyDescription(request.getDifficultyMode()) + "。";
    }

    private String difficultyDescription(String mode) {
        switch (mode) {
            case "EASY": return "偏基础";
            case "HARD": return "偏进阶";
            case "ADAPTIVE": return "自适应";
            default: return "均衡";
        }
    }

    private String buildRecommendation(SmartExamRequest request,
                                       List<Long> selectedIds,
                                       Set<Long> wrongQuestionIds,
                                       Map<Integer, Double> difficultyAccuracy) {
        StringBuilder recommendation = new StringBuilder();
        long wrongIncluded = selectedIds.stream().filter(wrongQuestionIds::contains).count();
        if (wrongIncluded > 0) {
            recommendation.append("本次试卷包含 ").append(wrongIncluded)
                    .append(" 道您的易错题，建议重点复习。");
        }
        if ("ADAPTIVE".equals(request.getDifficultyMode()) && !difficultyAccuracy.isEmpty()) {
            recommendation.append("基于您的历史答题表现，");
            difficultyAccuracy.entrySet().stream().filter(entry -> entry.getValue() < 0.5)
                    .forEach(entry -> recommendation.append("★".repeat(entry.getKey()))
                            .append("难度正确率较低，已适当增加练习。"));
        }
        return recommendation.length() == 0 ? "试卷已根据知识点覆盖和难度均衡原则智能生成。"
                : recommendation.toString();
    }
}
