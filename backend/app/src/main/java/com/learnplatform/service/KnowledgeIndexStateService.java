package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.KnowledgeContentBundle;
import com.learnplatform.entity.KnowledgeContentIndex;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.KnowledgeContentBundleMapper;
import com.learnplatform.mapper.KnowledgeContentIndexMapper;
import com.learnplatform.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeIndexStateService {
    private static final int LEASE_SECONDS = 120;
    private final KnowledgeContentBundleMapper bundles;
    private final KnowledgeContentIndexMapper indexes;
    private final UserMapper users;

    public KnowledgeIndexStateService(KnowledgeContentBundleMapper bundles, KnowledgeContentIndexMapper indexes,
                                      UserMapper users) {
        this.bundles = bundles;
        this.indexes = indexes;
        this.users = users;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void review(Long actorId, Long bundleId, String decision, String note) {
        requireAdmin(actorId);
        KnowledgeContentBundle bundle = requireBundle(bundleId);
        String reviewedNote = requireNote(note);
        if ("REVIEWED".equals(decision)) {
            if (!"PENDING".equals(bundle.getReviewStatus())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "知识版本不能恢复审核状态");
            }
            updateBundleReview(bundle, "REVIEWED", actorId, reviewedNote);
            return;
        }
        if (!"WITHDRAWN".equals(decision)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "知识审核决定无效");
        }
        updateBundleReview(bundle, "WITHDRAWN", actorId, reviewedNote);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public KnowledgeContentIndex begin(Long actorId, Long bundleId, String indexKey, String model, int dimensions,
                                       String collectionName, String vectorEndpointHash) {
        requireAdmin(actorId);
        KnowledgeContentBundle bundle = requireBundle(bundleId);
        requireReviewed(bundle);
        requireIndexMetadata(indexKey, model, dimensions, collectionName, vectorEndpointHash);
        var candidate = new KnowledgeContentIndex();
        candidate.setBundleId(bundleId);
        candidate.setIndexKey(indexKey);
        candidate.setModel(model);
        candidate.setDimensions(dimensions);
        candidate.setCollectionName(collectionName);
        candidate.setVectorEndpointHash(vectorEndpointHash);
        indexes.reserve(candidate);
        KnowledgeContentIndex index = indexes.lockById(candidate.getId());
        if (index == null) {
            throw new IllegalStateException("Knowledge index reservation failed");
        }
        if (!sameMetadata(index, model, dimensions, collectionName, vectorEndpointHash)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "索引键已绑定其他模型配置");
        }
        LocalDateTime now = LocalDateTime.now();
        if ("INDEXING".equals(index.getStatus()) && index.getLeaseUntil() != null
                && index.getLeaseUntil().isAfter(now)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "知识索引正在构建");
        }
        index.setStatus("INDEXING");
        index.setRunKey(UUID.randomUUID().toString());
        index.setLeaseUntil(now.plusSeconds(LEASE_SECONDS));
        index.setIndexedCount(0);
        if (indexes.updateById(index) != 1) {
            throw new IllegalStateException("Knowledge index start failed");
        }
        return index;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean renew(Long indexId, String runKey, int indexedCount) {
        return indexedCount >= 0 && validRunKey(runKey)
                && indexes.renew(indexId, runKey, indexedCount, LocalDateTime.now().plusSeconds(LEASE_SECONDS)) == 1;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean complete(Long indexId, String runKey, int indexedCount) {
        return indexedCount >= 0 && validRunKey(runKey) && indexes.complete(indexId, runKey, indexedCount) == 1;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean fail(Long indexId, String runKey) {
        return validRunKey(runKey) && indexes.fail(indexId, runKey) == 1;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean purged(Long indexId) {
        return indexId != null && indexId > 0 && indexes.purged(indexId) == 1;
    }

    public KnowledgeContentIndex ready(Long bundleId, String indexKey) {
        if (bundleId == null || bundleId <= 0 || !validIndexKey(indexKey)) {
            return null;
        }
        return indexes.ready(bundleId, indexKey);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<KnowledgeContentIndex> markDeleted(Long actorId, Long bundleId) {
        requireAdmin(actorId);
        KnowledgeContentBundle bundle = requireBundle(bundleId);
        updateBundleReview(bundle, "WITHDRAWN", actorId, "索引已撤回");
        List<KnowledgeContentIndex> result = indexes.listNonPurgedByBundle(bundleId);
        indexes.markDeletedByBundle(bundleId);
        return result;
    }

    private void requireAdmin(Long actorId) {
        User actor = actorId == null ? null : users.selectById(actorId);
        if (actor == null || !"ADMIN".equals(actor.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可管理知识索引");
        }
    }

    private KnowledgeContentBundle requireBundle(Long bundleId) {
        if (bundleId == null || bundleId <= 0) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "知识版本无效");
        }
        KnowledgeContentBundle bundle = bundles.lockById(bundleId);
        if (bundle == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识版本不存在");
        }
        return bundle;
    }

    private void requireReviewed(KnowledgeContentBundle bundle) {
        if (!"REVIEWED".equals(bundle.getReviewStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "知识版本尚未人工审核");
        }
    }

    private void updateBundleReview(KnowledgeContentBundle bundle, String status, Long actorId, String note) {
        bundle.setReviewStatus(status);
        var update = new UpdateWrapper<KnowledgeContentBundle>().eq("id", bundle.getId())
                .set("review_status", status).set("reviewed_by", actorId).set("reviewed_at", LocalDateTime.now())
                .set("review_note", note);
        if (bundles.update(null, update) != 1) {
            throw new IllegalStateException("Knowledge review update failed");
        }
    }

    private String requireNote(String note) {
        if (note == null || note.isBlank() || note.length() > 1000) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "审核说明不能为空且不能超过 1000 字符");
        }
        return note.trim();
    }

    private void requireIndexMetadata(String indexKey, String model, int dimensions, String collectionName,
                                      String vectorEndpointHash) {
        if (!validIndexKey(indexKey) || model == null || model.isBlank() || model.length() > 100
                || dimensions <= 0 || collectionName == null || collectionName.isBlank()
                || collectionName.length() > 200 || !validIndexKey(vectorEndpointHash)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "知识索引配置无效");
        }
    }

    private boolean sameMetadata(KnowledgeContentIndex index, String model, int dimensions, String collectionName,
                                 String vectorEndpointHash) {
        return model.equals(index.getModel()) && Integer.valueOf(dimensions).equals(index.getDimensions())
                && collectionName.equals(index.getCollectionName())
                && vectorEndpointHash.equals(index.getVectorEndpointHash());
    }

    private boolean validIndexKey(String indexKey) {
        return indexKey != null && indexKey.matches("[0-9a-f]{64}");
    }

    private boolean validRunKey(String runKey) {
        try {
            UUID.fromString(runKey);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
