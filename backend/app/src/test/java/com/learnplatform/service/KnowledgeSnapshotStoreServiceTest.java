package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.KnowledgeContentBundle;
import com.learnplatform.entity.KnowledgeContentChunk;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.KnowledgeContentBundleMapper;
import com.learnplatform.mapper.KnowledgeContentChunkMapper;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.service.knowledge.KnowledgeSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeSnapshotStoreServiceTest {
    private final KnowledgeContentBundleMapper bundles = mock(KnowledgeContentBundleMapper.class);
    private final KnowledgeContentChunkMapper chunks = mock(KnowledgeContentChunkMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final KnowledgeSnapshotStoreService service = new KnowledgeSnapshotStoreService(bundles, chunks, users);
    private final KnowledgeSnapshot snapshot = new KnowledgeSnapshot("course", "v1", "a".repeat(64),
            "revision", "review_pending", List.of(new KnowledgeSnapshot.Chunk("chunk", "concept", "title",
            "content", "b".repeat(64), "{}", "review_pending")));

    @BeforeEach void admin() {
        var user = new User();
        user.setRole("ADMIN");
        when(users.selectById(7L)).thenReturn(user);
    }

    @Test void preservesPendingReviewAndCopiesChunkContent() {
        reserve(snapshot.manifestHash());
        when(chunks.insert(any(KnowledgeContentChunk.class))).thenReturn(1);
        var result = service.store(7L, snapshot);
        assertEquals("PENDING", result.reviewStatus());
        assertFalse(result.alreadyImported());
        verify(chunks).insert(argThat((KnowledgeContentChunk value) -> value.getBundleId().equals(3L)
                && value.getContent().equals("content") && value.getContentHash().equals("b".repeat(64))));
    }

    @Test void unchangedVersionDoesNotInsertDuplicateChunks() {
        reserve(snapshot.manifestHash());
        when(chunks.countForBundle(3L)).thenReturn(1);
        assertTrue(service.store(7L, snapshot).alreadyImported());
        verify(chunks, never()).insert(any(KnowledgeContentChunk.class));
    }

    @Test void refusesChangedManifestForExistingVersion() {
        reserve("c".repeat(64));
        when(chunks.countForBundle(3L)).thenReturn(1);
        assertThrows(BusinessException.class, () -> service.store(7L, snapshot));
        verify(chunks, never()).insert(any(KnowledgeContentChunk.class));
    }

    @Test void rejectsNonAdminBeforePersistence() {
        when(users.selectById(7L)).thenReturn(new User());
        assertThrows(BusinessException.class, () -> service.store(7L, snapshot));
        verifyNoInteractions(bundles, chunks);
    }

    private void reserve(String hash) {
        doAnswer(invocation -> {
            KnowledgeContentBundle value = invocation.getArgument(0);
            value.setId(3L);
            value.setManifestHash(hash);
            when(bundles.lockById(3L)).thenReturn(value);
            return 1;
        }).when(bundles).reserve(any());
    }
}
