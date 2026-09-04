package com.learnplatform.service.question;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptivePracticePolicyTest {

    @Test
    void newLearnerPrefersIntroductoryToMediumQuestionsAndAllocatesRequestedTotal() {
        Map<Integer, AdaptivePracticePolicy.DifficultyStats> stats = Map.of(
                1, new AdaptivePracticePolicy.DifficultyStats(0, 0),
                2, new AdaptivePracticePolicy.DifficultyStats(0, 0),
                3, new AdaptivePracticePolicy.DifficultyStats(0, 0),
                4, new AdaptivePracticePolicy.DifficultyStats(0, 0),
                5, new AdaptivePracticePolicy.DifficultyStats(0, 0));

        double[] weights = AdaptivePracticePolicy.calculateWeights(stats);
        int[] allocation = AdaptivePracticePolicy.allocateCounts(weights, 20);

        assertTrue(weights[1] > weights[3]);
        assertTrue(weights[2] > weights[4]);
        assertEquals(20, Arrays.stream(allocation).sum());
    }

    @Test
    void summaryUsesObservedPerformanceAndNormalizedWeights() {
        Map<Integer, AdaptivePracticePolicy.DifficultyStats> stats = Map.of(
                1, new AdaptivePracticePolicy.DifficultyStats(10, 8),
                2, new AdaptivePracticePolicy.DifficultyStats(10, 5),
                3, new AdaptivePracticePolicy.DifficultyStats(0, 0),
                4, new AdaptivePracticePolicy.DifficultyStats(0, 0),
                5, new AdaptivePracticePolicy.DifficultyStats(0, 0));

        double[] weights = AdaptivePracticePolicy.calculateWeights(stats);
        Map<String, Object> summary = AdaptivePracticePolicy.buildSummary(stats, weights);

        assertEquals(20, summary.get("totalAnswered"));
        assertEquals(65.0, summary.get("overallCorrectRate"));
        assertEquals(1.0, AdaptivePracticePolicy.round(Arrays.stream(weights).sum()));
    }
}
