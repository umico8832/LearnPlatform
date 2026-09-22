package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.KnowledgeBundleVO;
import com.learnplatform.dto.KnowledgeChunkVO;
import com.learnplatform.dto.KnowledgeIndexStatusVO;
import com.learnplatform.entity.KnowledgeContentBundle;
import com.learnplatform.entity.KnowledgeContentChunk;
import com.learnplatform.entity.KnowledgeContentIndex;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.KnowledgeContentBundleMapper;
import com.learnplatform.mapper.KnowledgeContentChunkMapper;
import com.learnplatform.mapper.KnowledgeContentIndexMapper;
import com.learnplatform.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.function.Function;

@Service
public class KnowledgeQueryService {
    private final KnowledgeContentBundleMapper bundles;
    private final KnowledgeContentChunkMapper chunks;
    private final KnowledgeContentIndexMapper indexes;
    private final UserMapper users;

    public KnowledgeQueryService(KnowledgeContentBundleMapper bundles, KnowledgeContentChunkMapper chunks,
                                 KnowledgeContentIndexMapper indexes, UserMapper users) {
        this.bundles = bundles;
        this.chunks = chunks;
        this.indexes = indexes;
        this.users = users;
    }

    public Page<KnowledgeBundleVO> bundles(Long actorId, int pageNum, int pageSize,
                                           String courseKey, String reviewStatus) {
        requireAdmin(actorId);
        requirePage(pageNum, pageSize);
        requireFilter(courseKey, 100);
        if (reviewStatus != null && !Set.of("PENDING", "REVIEWED", "WITHDRAWN").contains(reviewStatus)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "知识审核状态无效");
        }
        var query = new LambdaQueryWrapper<KnowledgeContentBundle>()
                .eq(courseKey != null, KnowledgeContentBundle::getCourseKey, courseKey)
                .eq(reviewStatus != null, KnowledgeContentBundle::getReviewStatus, reviewStatus)
                .orderByDesc(KnowledgeContentBundle::getId);
        return project(bundles.selectPage(new Page<>(pageNum, pageSize), query), this::bundleView);
    }

    public KnowledgeBundleVO bundle(Long actorId, Long bundleId) {
        requireAdmin(actorId);
        return bundleView(requireBundle(bundleId));
    }

    public Page<KnowledgeChunkVO> chunks(Long actorId, Long bundleId, int pageNum, int pageSize, String conceptId) {
        requireAdmin(actorId);
        requirePage(pageNum, pageSize);
        requireFilter(conceptId, 150);
        requireBundle(bundleId);
        var query = new LambdaQueryWrapper<KnowledgeContentChunk>()
                .eq(KnowledgeContentChunk::getBundleId, bundleId)
                .eq(conceptId != null, KnowledgeContentChunk::getConceptKey, conceptId)
                .orderByAsc(KnowledgeContentChunk::getId);
        return project(chunks.selectPage(new Page<>(pageNum, pageSize), query), chunk -> new KnowledgeChunkVO(
                chunk.getId(), chunk.getBundleId(), chunk.getChunkKey(), chunk.getConceptKey(), chunk.getTitle(),
                chunk.getContent(), chunk.getContentHash(), chunk.getMetadataJson(), chunk.getSourceQualityStatus()));
    }

    public Page<KnowledgeIndexStatusVO> indexes(Long actorId, Long bundleId, int pageNum, int pageSize) {
        requireAdmin(actorId);
        requirePage(pageNum, pageSize);
        requireBundle(bundleId);
        var query = new LambdaQueryWrapper<KnowledgeContentIndex>()
                .eq(KnowledgeContentIndex::getBundleId, bundleId).orderByDesc(KnowledgeContentIndex::getId);
        return project(indexes.selectPage(new Page<>(pageNum, pageSize), query), index -> new KnowledgeIndexStatusVO(
                index.getId(), index.getBundleId(), index.getIndexKey(), index.getModel(), index.getDimensions(),
                index.getStatus(), index.getIndexedCount(), index.getLeaseUntil()));
    }

    private void requireAdmin(Long actorId) {
        User actor = actorId == null ? null : users.selectById(actorId);
        if (actor == null || !"ADMIN".equals(actor.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可查看知识审核资料");
        }
    }

    private KnowledgeContentBundle requireBundle(Long bundleId) {
        if (bundleId == null || bundleId <= 0) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "知识版本无效");
        }
        KnowledgeContentBundle bundle = bundles.selectById(bundleId);
        if (bundle == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识版本不存在");
        }
        return bundle;
    }

    private void requirePage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 50) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "页码至少为 1，每页数量须在 1 到 50 之间");
        }
    }

    private void requireFilter(String value, int limit) {
        if (value != null && (value.isBlank() || value.length() > limit)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "知识筛选条件无效");
        }
    }

    private KnowledgeBundleVO bundleView(KnowledgeContentBundle bundle) {
        return new KnowledgeBundleVO(bundle.getId(), bundle.getCourseKey(), bundle.getBundleVersion(),
                bundle.getManifestHash(), bundle.getSourceRevision(), bundle.getSourceQualityStatus(),
                bundle.getReviewStatus(), bundle.getChunkCount(), bundle.getImportedBy(), bundle.getImportedAt(),
                bundle.getReviewedBy(), bundle.getReviewedAt(), bundle.getReviewNote());
    }

    private <S, T> Page<T> project(Page<S> source, Function<S, T> mapper) {
        var result = new Page<T>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(mapper).toList());
        return result;
    }
}
