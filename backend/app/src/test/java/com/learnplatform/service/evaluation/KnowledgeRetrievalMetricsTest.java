package com.learnplatform.service.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeRetrievalMetricsTest {
    @Test void calculatesMetricsFromOnlyTheTopKResults() {
        var result = KnowledgeRetrievalMetrics.evaluate(
                Set.of("stack-push", "queue-fifo"),
                List.of("binary-search", "stack-push", "queue-fifo", "bst-order"), 2);

        assertEquals(0.5, result.recallAtK());
        assertEquals(0.5, result.reciprocalRankAtK());
        assertTrue(result.hitAtK());
    }

    @Test void acceptsAnEmptyReturnSetAsZeroMetrics() {
        var result = KnowledgeRetrievalMetrics.evaluate(Set.of("queue-fifo"), List.of(), 3);

        assertEquals(0.0, result.recallAtK());
        assertEquals(0.0, result.reciprocalRankAtK());
        assertFalse(result.hitAtK());
    }

    @Test void rejectsInvalidEvaluationInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeRetrievalMetrics.evaluate(Set.of(), List.of("stack-push"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeRetrievalMetrics.evaluate(Set.of("stack-push"), List.of(" "), 1));
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeRetrievalMetrics.evaluate(Set.of("stack-push"),
                        List.of("queue-fifo", "queue-fifo"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeRetrievalMetrics.evaluate(Set.of("stack-push"), List.of(), 0));
    }
}
