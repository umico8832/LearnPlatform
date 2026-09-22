package com.learnplatform.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import com.learnplatform.config.KnowledgeConfig;
import com.learnplatform.service.knowledge.vector.KnowledgeVectorSpace;
import com.learnplatform.service.knowledge.vector.QdrantKnowledgeVectorStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class KnowledgeVectorService {
    private final KnowledgeConfig config;
    private final AiConfig ai;

    public KnowledgeVectorService(KnowledgeConfig config, AiConfig ai) {
        this.config = config;
        this.ai = ai;
    }

    public KnowledgeVectorSpace space() {
        var embedding = ai.getEmbedding();
        if (!config.isVectorEnabled() || !embedding.isEnabled() || embedding.getDimensions() == null
                || embedding.getDimensions() <= 0 || embedding.getDimensions() > 65536
                || embedding.getModel() == null || embedding.getModel().isBlank()
                || config.getVectorCollection() == null || config.getVectorCollection().isBlank()
                || config.getVectorTimeoutSeconds() <= 0 || config.getVectorTimeoutSeconds() > 60) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "知识向量索引尚未配置完成");
        }
        var identity = JsonNodeFactory.instance.arrayNode().add("knowledge-index-v1")
                .add(embedding.getApiBaseUrl()).add(embedding.getModel()).add(embedding.getDimensions())
                .add(config.getVectorUrl()).add(config.getVectorCollection()).add("Cosine");
        try {
            String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.toString().getBytes(StandardCharsets.UTF_8)));
            return new KnowledgeVectorSpace(key, embedding.getModel(), embedding.getDimensions(),
                    config.getVectorCollection(), endpointHash());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    public String endpointHash() {
        if (config.getVectorUrl() == null || config.getVectorUrl().isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "知识向量服务尚未配置完成");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    config.getVectorUrl().replaceAll("/+$", "").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    public QdrantKnowledgeVectorStore client() {
        if (!config.isVectorEnabled() || config.getVectorTimeoutSeconds() <= 0
                || config.getVectorTimeoutSeconds() > 60) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "知识向量服务尚未配置完成");
        }
        return new QdrantKnowledgeVectorStore(config.getVectorUrl(), config.getVectorApiKey(),
                config.getVectorCollection(), Duration.ofSeconds(config.getVectorTimeoutSeconds()));
    }
}
