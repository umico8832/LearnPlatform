package com.learnplatform.service.exam;

import com.learnplatform.entity.Question;
import com.learnplatform.service.AiExamGenerationService.SmartExamRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 根据现有组卷规则为候选题评分并平衡知识点与难度。 */
@Service
public class AiExamQuestionSelectionService {

    public List<Long> select(List<Question> questions,
                             Map<Long, List<Long>> questionKnowledgePoints,
                             Set<Long> wrongQuestionIds,
                             Map<Integer, Double> difficultyAccuracy,
                             SmartExamRequest request,
                             int totalCount) {
        Map<Long, Double> scores = new HashMap<>();
        for (Question question : questions) {
            scores.put(question.getId(), calculatePriority(question, wrongQuestionIds, difficultyAccuracy, request));
        }
        return selectBalanced(questions, scores, questionKnowledgePoints,
                calculateDifficultyTargets(totalCount, request.getDifficultyMode(), difficultyAccuracy), totalCount);
    }

    private double calculatePriority(Question question, Set<Long> wrongQuestionIds,
                                     Map<Integer, Double> difficultyAccuracy, SmartExamRequest request) {
        double score = 0.0;
        if (wrongQuestionIds.contains(question.getId())) {
            score += 30.0;
        }
        if (question.getDifficulty() != null && !difficultyAccuracy.isEmpty()) {
            score += calculateDifficultyWeight(question.getDifficulty(), difficultyAccuracy,
                    request.getDifficultyMode());
        }
        if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
            score += 5.0;
        }
        if (question.getQuestionType() != null) {
            score += 2.0;
        }
        return score + Math.random() * 10.0;
    }

    private double calculateDifficultyWeight(int difficulty, Map<Integer, Double> accuracy, String mode) {
        switch (mode) {
            case "EASY": return difficulty <= 2 ? 15.0 : (difficulty == 3 ? 5.0 : -5.0);
            case "HARD": return difficulty >= 4 ? 15.0 : (difficulty == 3 ? 5.0 : -5.0);
            case "ADAPTIVE":
                Double userAccuracy = accuracy.get(difficulty);
                if (userAccuracy == null) { return 5.0; }
                if (userAccuracy > 0.8 && difficulty < 5) { return 15.0; }
                if (userAccuracy < 0.5) { return 10.0; }
                return 8.0;
            default: return 8.0;
        }
    }

    private Map<Integer, Integer> calculateDifficultyTargets(int total, String mode,
                                                              Map<Integer, Double> accuracy) {
        Map<Integer, Integer> targets = new LinkedHashMap<>();
        switch (mode) {
            case "EASY":
                targets.put(1, (int) Math.ceil(total * 0.3));
                targets.put(2, (int) Math.ceil(total * 0.35));
                targets.put(3, (int) Math.ceil(total * 0.25));
                targets.put(4, (int) Math.floor(total * 0.1));
                targets.put(5, 0);
                break;
            case "HARD":
                targets.put(1, 0);
                targets.put(2, (int) Math.floor(total * 0.1));
                targets.put(3, (int) Math.ceil(total * 0.25));
                targets.put(4, (int) Math.ceil(total * 0.35));
                targets.put(5, (int) Math.ceil(total * 0.3));
                break;
            case "ADAPTIVE":
                for (int difficulty = 1; difficulty <= 5; difficulty++) {
                    Double value = accuracy.get(difficulty);
                    if (value == null) {
                        targets.put(difficulty, (int) Math.ceil(total * 0.2));
                    } else if (value > 0.8 || value < 0.4) {
                        targets.put(difficulty, (int) Math.ceil(total * 0.3));
                    } else {
                        targets.put(difficulty, (int) Math.ceil(total * 0.2));
                    }
                }
                break;
            default:
                int perLevel = total / 5;
                int remainder = total % 5;
                for (int difficulty = 1; difficulty <= 5; difficulty++) {
                    targets.put(difficulty, perLevel + (difficulty <= remainder ? 1 : 0));
                }
        }
        return targets;
    }

    private List<Long> selectBalanced(List<Question> questions, Map<Long, Double> scores,
                                      Map<Long, List<Long>> questionKnowledgePoints,
                                      Map<Integer, Integer> difficultyTargets, int totalCount) {
        List<Question> sorted = questions.stream()
                .sorted((left, right) -> Double.compare(scores.getOrDefault(right.getId(), 0.0),
                        scores.getOrDefault(left.getId(), 0.0)))
                .collect(Collectors.toList());
        Set<Long> selected = new LinkedHashSet<>();
        Map<Integer, Integer> difficultyCount = new HashMap<>();
        Set<Long> allKnowledgePoints = questionKnowledgePoints.values().stream()
                .flatMap(Collection::stream).collect(Collectors.toSet());
        for (Long knowledgePointId : allKnowledgePoints) {
            if (selected.size() >= totalCount) { break; }
            Question candidate = sorted.stream().filter(question -> !selected.contains(question.getId()))
                    .filter(question -> questionKnowledgePoints.get(question.getId()) != null
                            && questionKnowledgePoints.get(question.getId()).contains(knowledgePointId))
                    .findFirst().orElse(null);
            if (candidate != null) {
                selected.add(candidate.getId());
                difficultyCount.merge(candidate.getDifficulty() != null ? candidate.getDifficulty() : 3,
                        1, Integer::sum);
            }
        }
        for (Map.Entry<Integer, Integer> entry : difficultyTargets.entrySet()) {
            int difficulty = entry.getKey();
            int current = difficultyCount.getOrDefault(difficulty, 0);
            while (current < entry.getValue() && selected.size() < totalCount) {
                Question candidate = sorted.stream().filter(question -> !selected.contains(question.getId()))
                        .filter(question -> Objects.equals(question.getDifficulty(), difficulty))
                        .findFirst().orElse(null);
                if (candidate == null) { break; }
                selected.add(candidate.getId());
                difficultyCount.put(difficulty, ++current);
            }
        }
        for (Question question : sorted) {
            if (selected.size() >= totalCount) { break; }
            selected.add(question.getId());
        }
        return new ArrayList<>(selected);
    }
}
