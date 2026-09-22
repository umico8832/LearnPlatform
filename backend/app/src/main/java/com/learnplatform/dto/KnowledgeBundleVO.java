package com.learnplatform.dto;

import java.time.LocalDateTime;

public record KnowledgeBundleVO(Long bundleId, String courseKey, String version, String manifestHash,
                                String sourceRevision, String sourceQualityStatus, String reviewStatus,
                                Integer chunkCount, Long importedBy, LocalDateTime importedAt,
                                Long reviewedBy, LocalDateTime reviewedAt, String reviewNote) { }
