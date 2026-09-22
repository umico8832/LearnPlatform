package com.learnplatform.service;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.KnowledgeConfig;
import com.learnplatform.dto.KnowledgeIndexVO;
import com.learnplatform.entity.KnowledgeContentChunk;
import com.learnplatform.mapper.KnowledgeContentChunkMapper;
import com.learnplatform.service.ai.AiCallContext;
import com.learnplatform.service.knowledge.vector.QdrantKnowledgeVectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Service
public class KnowledgeIndexService {
    private final AiInvocationService invocation;
    private final KnowledgeIndexStateService states;
    private final KnowledgeContentChunkMapper chunks;
    private final KnowledgeVectorService vectors;
    private final KnowledgeConfig config;

    public KnowledgeIndexService(AiInvocationService invocation, KnowledgeIndexStateService states,
                                  KnowledgeContentChunkMapper chunks, KnowledgeVectorService vectors,
                                  KnowledgeConfig config) {
        this.invocation = invocation;
        this.states = states;
        this.chunks = chunks;
        this.vectors = vectors;
        this.config = config;
    }

    public KnowledgeIndexVO index(Long actorId, Long bundleId) {
        var space = vectors.space();
        int batchSize = config.getEmbeddingBatchSize();
        if (batchSize <= 0 || batchSize > 32) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "知识索引批次必须介于 1 与 32 之间");
        }
        var state = states.begin(actorId, bundleId, space.indexKey(), space.model(), space.dimensions(),
                space.collection(), space.endpointHash());
        int indexed = 0;
        try (var client = vectors.client()) {
            List<KnowledgeContentChunk> content = chunks.listForBundle(bundleId);
            if (content.isEmpty() || content.stream().anyMatch(chunk -> chunk.getContent() == null
                    || chunk.getContent().isBlank() || chunk.getContent().length() > 6000)) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "知识片段为空或过长，请重新分片后导入新版本");
            }
            client.ensureCollection(space.dimensions());
            for (int start = 0; start < content.size(); start += batchSize) {
                requireLease(states.renew(state.getId(), state.getRunKey(), indexed));
                var batch = content.subList(start, Math.min(start + batchSize, content.size()));
                var request = new EmbeddingRequest(space.model(), batch.stream().map(
                        KnowledgeContentChunk::getContent).toList(), space.dimensions());
                var result = invocation.embed(new AiCallContext(actorId, "knowledge_embedding",
                        UUID.fromString(state.getRunKey())), request, new Cancellation());
                if (result.vectors().size() != batch.size()
                        || result.model() != null && !space.model().equals(result.model())) {
                    throw new BusinessException(ResultCode.BUSINESS_ERROR, "Embedding 模型或向量数量与索引配置不一致");
                }
                requireLease(states.renew(state.getId(), state.getRunKey(), indexed));
                var points = new ArrayList<QdrantKnowledgeVectorStore.Point>();
                for (int i = 0; i < batch.size(); i++) {
                    points.add(new QdrantKnowledgeVectorStore.Point(batch.get(i).getChunkKey(),
                            result.vectors().get(i)));
                }
                client.upsert(bundleId, space.indexKey(), points);
                indexed += batch.size();
            }
            requireLease(states.complete(state.getId(), state.getRunKey(), indexed));
            return new KnowledgeIndexVO(state.getId(), bundleId, space.indexKey(), "READY", indexed);
        } catch (RuntimeException exception) {
            states.fail(state.getId(), state.getRunKey());
            throw exception;
        }
    }

    public void withdraw(Long actorId, Long bundleId) {
        var indexes = states.markDeleted(actorId, bundleId);
        if (indexes.isEmpty()) {
            return;
        }
        String endpointHash = vectors.endpointHash();
        boolean remaining = false;
        try (var client = vectors.client()) {
            for (var index : indexes) {
                if (index.getLeaseUntil() != null && index.getLeaseUntil().isAfter(LocalDateTime.now())) {
                    remaining = true;
                    continue;
                }
                if (!index.getVectorEndpointHash().equals(endpointHash)
                        || !index.getCollectionName().equals(config.getVectorCollection())) {
                    remaining = true;
                    continue;
                }
                client.delete(bundleId, index.getIndexKey());
                states.purged(index.getId());
            }
        }
        if (remaining) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "知识已撤回；请等待运行中的任务租约结束，或恢复旧索引的向量端点与集合配置后重试清理");
        }
    }

    private void requireLease(boolean valid) {
        if (!valid) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "知识索引任务已失效或内容已撤回");
        }
    }
}
