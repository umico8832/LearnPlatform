package com.learnplatform.dto;

import com.learnplatform.entity.KnowledgeContentIndex;

public record KnowledgeIndexVO(Long indexId, Long bundleId, String indexKey, String status, int indexedCount) {
    public static KnowledgeIndexVO from(KnowledgeContentIndex value) {
        return new KnowledgeIndexVO(value.getId(), value.getBundleId(), value.getIndexKey(), value.getStatus(),
                value.getIndexedCount());
    }
}
