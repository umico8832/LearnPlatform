package com.learnplatform.service.question;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AdaptivePracticePolicy {
    private AdaptivePracticePolicy() {
    }

    public static double[] calculateWeights(Map<Integer, DifficultyStats> statsMap) {
        double[] weights = {1.0, 1.0, 1.0, 1.0, 1.0};
        boolean hasAnyData = statsMap.values().stream().anyMatch(stats -> stats.total() > 0);
        if (!hasAnyData) {
            return normalizeWeights(new double[]{2.5, 3.0, 2.5, 1.0, 0.5});
        }
        for (int difficulty = 1; difficulty <= 5; difficulty++) {
            DifficultyStats stats = statsMap.getOrDefault(difficulty, new DifficultyStats(0, 0));
            if (stats.total() == 0) {
                weights[difficulty - 1] = 1.5;
                continue;
            }
            double correctRate = (double) stats.correct() / stats.total();
            if (correctRate > 0.75) {
                weights[difficulty - 1] = 0.5;
                if (difficulty < 5) {
                    weights[difficulty] += 2.0;
                }
                if (difficulty < 4) {
                    weights[difficulty + 1] += 0.5;
                }
            } else if (correctRate >= 0.5) {
                weights[difficulty - 1] += 2.0;
            } else {
                weights[difficulty - 1] += 2.5;
                if (difficulty > 1) {
                    weights[difficulty - 2] += 1.5;
                }
                if (difficulty > 2) {
                    weights[difficulty - 3] += 0.5;
                }
            }
            double confidenceFactor = Math.min(stats.total() / 10.0, 1.0);
            weights[difficulty - 1] *= confidenceFactor + 0.3;
        }
        return normalizeWeights(weights);
    }

    public static int[] allocateCounts(double[] weights, int total) {
        int[] counts = new int[5];
        double sum = 0;
        for (double weight : weights) {
            sum += weight;
        }
        int allocated = 0;
        for (int index = 0; index < counts.length; index++) {
            if (index == counts.length - 1) {
                counts[index] = total - allocated;
            } else {
                counts[index] = (int) Math.round(weights[index] / sum * total);
                allocated += counts[index];
            }
            counts[index] = Math.max(counts[index], 0);
        }
        return counts;
    }

    public static Map<String, Object> buildSummary(
            Map<Integer, DifficultyStats> statsMap, double[] weights) {
        Map<String, Object> summary = new LinkedHashMap<>();
        int totalAnswered = statsMap.values().stream().mapToInt(DifficultyStats::total).sum();
        int totalCorrect = statsMap.values().stream().mapToInt(DifficultyStats::correct).sum();
        summary.put("totalAnswered", totalAnswered);
        summary.put("overallCorrectRate", totalAnswered > 0
                ? round(totalCorrect * 100.0 / totalAnswered) : 0.0);

        String[] labels = {"入门", "简单", "中等", "困难", "专家"};
        List<Map<String, Object>> details = new ArrayList<>();
        for (int difficulty = 1; difficulty <= 5; difficulty++) {
            DifficultyStats stats = statsMap.getOrDefault(difficulty, new DifficultyStats(0, 0));
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("difficulty", difficulty);
            detail.put("label", labels[difficulty - 1]);
            detail.put("total", stats.total());
            detail.put("correct", stats.correct());
            detail.put("correctRate", stats.total() > 0
                    ? round(stats.correct() * 100.0 / stats.total()) : 0);
            detail.put("weight", round(weights[difficulty - 1]));
            details.add(detail);
        }
        summary.put("difficultyDetails", details);
        double weightedDifficulty = 0;
        double weightSum = 0;
        for (int index = 0; index < weights.length; index++) {
            weightedDifficulty += (index + 1) * weights[index];
            weightSum += weights[index];
        }
        summary.put("recommendedDifficulty", round(
                weightSum > 0 ? weightedDifficulty / weightSum : 3.0));
        return summary;
    }

    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double[] normalizeWeights(double[] weights) {
        double sum = 0;
        for (double weight : weights) {
            sum += Math.max(weight, 0.01);
        }
        double[] normalized = new double[weights.length];
        for (int index = 0; index < weights.length; index++) {
            normalized[index] = Math.max(weights[index], 0.01) / sum;
        }
        return normalized;
    }

    public record DifficultyStats(int total, int correct) { }
}
