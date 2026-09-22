package com.learnplatform.dto;

public record KnowledgeChunkVO(Long id, Long bundleId, String chunkId, String conceptId, String title,
                               String text, String contentHash, String metadataJson, String sourceQualityStatus) { }
