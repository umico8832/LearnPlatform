package com.learnplatform.service.evaluation;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

final class KnowledgeRetrievalMetrics {
    private KnowledgeRetrievalMetrics() { }

    static Result evaluate(Set<String> relevantIds, List<String> returnedIds, int k) {
        if (relevantIds == null || relevantIds.isEmpty() || returnedIds == null || k <= 0) {
            throw new IllegalArgumentException("Relevant IDs and a positive cutoff are required");
        }
        for (String relevantId : relevantIds) {
            if (relevantId == null || relevantId.isBlank()) {
                throw new IllegalArgumentException("Relevant IDs must be nonblank");
            }
        }
        Set<String> returned = new HashSet<>();
        for (String returnedId : returnedIds) {
            if (returnedId == null || returnedId.isBlank() || !returned.add(returnedId)) {
                throw new IllegalArgumentException("Returned IDs must be nonblank and unique");
            }
        }
        int relevantCount = 0;
        int firstRelevantRank = 0;
        int limit = Math.min(k, returnedIds.size());
        for (int index = 0; index < limit; index++) {
            if (relevantIds.contains(returnedIds.get(index))) {
                relevantCount++;
                if (firstRelevantRank == 0) {
                    firstRelevantRank = index + 1;
                }
            }
        }
        return new Result((double) relevantCount / relevantIds.size(),
                firstRelevantRank == 0 ? 0.0 : 1.0 / firstRelevantRank, firstRelevantRank != 0);
    }

    record Result(double recallAtK, double reciprocalRankAtK, boolean hitAtK) { }
}
