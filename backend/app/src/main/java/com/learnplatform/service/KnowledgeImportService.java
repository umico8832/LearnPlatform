package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.KnowledgeConfig;
import com.learnplatform.dto.KnowledgeImportVO;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.service.knowledge.KnowledgeSnapshot;
import com.learnplatform.service.knowledge.KnowledgeSnapshotLoader;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class KnowledgeImportService {
    private final KnowledgeConfig config;
    private final KnowledgeSnapshotLoader loader;
    private final KnowledgeSnapshotStoreService store;
    private final UserMapper users;

    public KnowledgeImportService(KnowledgeConfig config, KnowledgeSnapshotLoader loader,
                                   KnowledgeSnapshotStoreService store, UserMapper users) {
        this.config = config;
        this.loader = loader;
        this.store = store;
        this.users = users;
    }

    public KnowledgeImportVO importConfigured(Long actorId) {
        User actor = actorId == null ? null : users.selectById(actorId);
        if (actor == null || !"ADMIN".equals(actor.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可导入知识快照");
        }
        if (config.getSnapshotPath() == null || config.getSnapshotPath().isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "尚未配置知识快照目录");
        }
        KnowledgeSnapshot snapshot;
        try {
            snapshot = loader.load(Path.of(config.getSnapshotPath()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "知识快照校验失败，请核对清单、课程与文件完整性");
        }
        return store.store(actorId, snapshot);
    }
}
