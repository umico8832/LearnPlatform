package com.learnplatform.service.knowledge;

import java.util.List;

public record KnowledgeSnapshot(
        String courseKey,
        String version,
        String manifestHash,
        String sourceRevision,
        String qualityStatus,
        List<Chunk> chunks) {

    public KnowledgeSnapshot {
        chunks = List.copyOf(chunks);
    }

    public record Chunk(
            String id,
            String conceptId,
            String title,
            String text,
            String contentHash,
            String metadataJson,
            String qualityStatus) {
    }
}
