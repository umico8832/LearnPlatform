package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.KnowledgeImportVO;
import com.learnplatform.entity.KnowledgeContentBundle;
import com.learnplatform.entity.KnowledgeContentChunk;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.KnowledgeContentBundleMapper;
import com.learnplatform.mapper.KnowledgeContentChunkMapper;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.service.knowledge.KnowledgeSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class KnowledgeSnapshotStoreService {
    private final KnowledgeContentBundleMapper bundles;
    private final KnowledgeContentChunkMapper chunks;
    private final UserMapper users;

    public KnowledgeSnapshotStoreService(KnowledgeContentBundleMapper bundles,
                                         KnowledgeContentChunkMapper chunks, UserMapper users) {
        this.bundles = bundles;
        this.chunks = chunks;
        this.users = users;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public KnowledgeImportVO store(Long actorId, KnowledgeSnapshot snapshot) {
        User actor = actorId == null ? null : users.selectById(actorId);
        if (actor == null || !"ADMIN".equals(actor.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可导入知识快照");
        }
        var candidate = new KnowledgeContentBundle();
        candidate.setCourseKey(snapshot.courseKey());
        candidate.setBundleVersion(snapshot.version());
        candidate.setManifestHash(snapshot.manifestHash());
        candidate.setSourceRevision(snapshot.sourceRevision());
        candidate.setSourceQualityStatus(snapshot.qualityStatus());
        candidate.setReviewStatus("PENDING");
        candidate.setChunkCount(snapshot.chunks().size());
        candidate.setImportedBy(actorId);
        bundles.reserve(candidate);
        KnowledgeContentBundle bundle = bundles.lockById(candidate.getId());
        if (bundle == null) {
            throw new IllegalStateException("Knowledge bundle reservation failed");
        }
        if (!bundle.getManifestHash().equals(snapshot.manifestHash())
                || bundle.getChunkCount() != snapshot.chunks().size()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "知识版本内容已变化，请创建新版本后导入");
        }
        int count = chunks.countForBundle(bundle.getId());
        if (count != 0 && count != bundle.getChunkCount()) {
            throw new IllegalStateException("Knowledge bundle is incomplete");
        }
        if (count == 0) {
            for (var source : snapshot.chunks()) {
                var chunk = new KnowledgeContentChunk();
                chunk.setBundleId(bundle.getId());
                chunk.setChunkKey(source.id());
                chunk.setConceptKey(source.conceptId());
                chunk.setTitle(source.title());
                chunk.setContent(source.text());
                chunk.setContentHash(source.contentHash());
                chunk.setMetadataJson(source.metadataJson());
                chunk.setSourceQualityStatus(source.qualityStatus());
                if (chunks.insert(chunk) != 1) {
                    throw new IllegalStateException("Knowledge chunk persistence failed");
                }
            }
        }
        return new KnowledgeImportVO(bundle.getId(), bundle.getCourseKey(), bundle.getBundleVersion(),
                bundle.getManifestHash(), bundle.getReviewStatus(), bundle.getChunkCount(), count != 0);
    }
}
