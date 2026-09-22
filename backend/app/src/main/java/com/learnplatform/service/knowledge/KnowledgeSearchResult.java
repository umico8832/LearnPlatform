package com.learnplatform.service.knowledge;

import java.util.List;

public record KnowledgeSearchResult(List<Citation> citations) {
    public KnowledgeSearchResult {
        citations = List.copyOf(citations);
    }

    public record Citation(Long bundleId, String version, String chunkId, String conceptId,
                            String title, String text, String contentHash) { }
}
