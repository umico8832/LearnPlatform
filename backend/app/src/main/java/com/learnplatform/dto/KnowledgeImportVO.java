package com.learnplatform.dto;

public record KnowledgeImportVO(Long bundleId, String courseKey, String version, String manifestHash,
                                 String reviewStatus, int chunkCount, boolean alreadyImported) { }
