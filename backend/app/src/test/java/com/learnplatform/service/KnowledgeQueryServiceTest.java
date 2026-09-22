package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.KnowledgeContentBundleMapper;
import com.learnplatform.mapper.KnowledgeContentChunkMapper;
import com.learnplatform.mapper.KnowledgeContentIndexMapper;
import com.learnplatform.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeQueryServiceTest {
    private final KnowledgeContentBundleMapper bundles = mock(KnowledgeContentBundleMapper.class);
    private final KnowledgeContentChunkMapper chunks = mock(KnowledgeContentChunkMapper.class);
    private final KnowledgeContentIndexMapper indexes = mock(KnowledgeContentIndexMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final KnowledgeQueryService service = new KnowledgeQueryService(bundles, chunks, indexes, users);

    @Test void everyReadRequiresCurrentDatabaseAdminBeforeReadingKnowledge() {
        var learner = new User();
        learner.setRole("USER");
        when(users.selectById(7L)).thenReturn(learner);
        assertCode(1003, () -> service.bundles(7L, 1, 20, null, null));
        assertCode(1003, () -> service.bundle(7L, 1L));
        assertCode(1003, () -> service.chunks(7L, 1L, 1, 20, null));
        assertCode(1003, () -> service.indexes(7L, 1L, 1, 20));
        assertCode(1003, () -> service.bundle(null, 1L));
        assertCode(1003, () -> service.bundle(99L, 1L));
        verifyNoInteractions(bundles, chunks, indexes);
    }

    @Test void invalidPaginationAndFiltersNeverQueryContent() {
        admin();
        assertCode(1001, () -> service.bundles(7L, 0, 20, null, null));
        assertCode(1001, () -> service.bundles(7L, 1, 51, null, null));
        assertCode(1001, () -> service.chunks(7L, 1L, 1, 0, null));
        assertCode(1001, () -> service.indexes(7L, 1L, -1, 20));
        assertCode(1001, () -> service.bundles(7L, 1, 20, " ", null));
        assertCode(1001, () -> service.bundles(7L, 1, 20, "a".repeat(101), null));
        assertCode(1001, () -> service.bundles(7L, 1, 20, null, "UNKNOWN"));
        assertCode(1001, () -> service.chunks(7L, 1L, 1, 20, "a".repeat(151)));
        verifyNoInteractions(bundles, chunks, indexes);
    }

    @Test void invalidAndMissingVersionsCannotReturnUnscopedContent() {
        admin();
        assertCode(1001, () -> service.bundle(7L, 0L));
        assertCode(1001, () -> service.bundle(7L, null));
        assertCode(1004, () -> service.bundle(7L, 88L));
        assertCode(1004, () -> service.chunks(7L, 88L, 1, 20, null));
        assertCode(1004, () -> service.indexes(7L, 88L, 1, 20));
        verifyNoInteractions(chunks, indexes);
    }

    private void admin() {
        var admin = new User();
        admin.setRole("ADMIN");
        when(users.selectById(7L)).thenReturn(admin);
    }

    private void assertCode(int code, Runnable action) {
        assertEquals(code, assertThrows(BusinessException.class, action::run).getCode());
    }
}
