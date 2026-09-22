package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.config.KnowledgeConfig;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.service.knowledge.KnowledgeSnapshotLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeImportServiceTest {
    private final KnowledgeConfig config = new KnowledgeConfig();
    private final KnowledgeSnapshotLoader loader = mock(KnowledgeSnapshotLoader.class);
    private final KnowledgeSnapshotStoreService store = mock(KnowledgeSnapshotStoreService.class);
    private final UserMapper users = mock(UserMapper.class);
    private final KnowledgeImportService service = new KnowledgeImportService(config, loader, store, users);

    @Test void missingConfigurationNeverReadsOrWrites() {
        var admin = new User();
        admin.setRole("ADMIN");
        when(users.selectById(7L)).thenReturn(admin);
        assertThrows(BusinessException.class, () -> service.importConfigured(7L));
        verifyNoInteractions(loader, store);
    }

    @Test void unauthorizedUserNeverReadsFiles() {
        assertThrows(BusinessException.class, () -> service.importConfigured(7L));
        verifyNoInteractions(loader, store);
    }

    @Test void rejectsMalformedSnapshotBeforeDatabaseAndSanitizesFailure() {
        var admin = new User();
        admin.setRole("ADMIN");
        when(users.selectById(7L)).thenReturn(admin);
        config.setSnapshotPath("/configured/knowledge");
        when(loader.load(Path.of("/configured/knowledge"))).thenThrow(new IllegalArgumentException("private-body"));
        var error = assertThrows(BusinessException.class, () -> service.importConfigured(7L));
        assertFalse(error.getMessage().contains("private-body"));
        verifyNoInteractions(store);
    }
}
