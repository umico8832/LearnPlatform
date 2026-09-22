package com.learnplatform.dto;

import java.time.LocalDateTime;

public record KnowledgeIndexStatusVO(Long indexId, Long bundleId, String indexKey, String model, Integer dimensions,
                                     String status, Integer indexedCount, LocalDateTime leaseUntil) { }
