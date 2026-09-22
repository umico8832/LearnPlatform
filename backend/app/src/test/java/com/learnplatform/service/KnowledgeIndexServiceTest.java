package com.learnplatform.service;

import com.learnplatform.ai.model.EmbeddingResult;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.config.KnowledgeConfig;
import com.learnplatform.entity.KnowledgeContentChunk;
import com.learnplatform.entity.KnowledgeContentIndex;
import com.learnplatform.mapper.KnowledgeContentChunkMapper;
import com.learnplatform.service.knowledge.vector.KnowledgeVectorSpace;
import com.learnplatform.service.knowledge.vector.QdrantKnowledgeVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeIndexServiceTest {
    private final AiInvocationService invocation = mock(AiInvocationService.class);
    private final KnowledgeIndexStateService states = mock(KnowledgeIndexStateService.class);
    private final KnowledgeContentChunkMapper chunks = mock(KnowledgeContentChunkMapper.class);
    private final KnowledgeVectorService vectors = mock(KnowledgeVectorService.class);
    private final KnowledgeConfig config = new KnowledgeConfig();
    private final QdrantKnowledgeVectorStore client = mock(QdrantKnowledgeVectorStore.class);
    private final KnowledgeIndexService service = new KnowledgeIndexService(invocation, states, chunks, vectors, config);
    private final String key = "a".repeat(64);
    private final String endpoint = "b".repeat(64);
    private final String run = UUID.randomUUID().toString();

    @BeforeEach void ready() {
        when(vectors.space()).thenReturn(new KnowledgeVectorSpace(key, "model", 2, "collection", endpoint));
        var state = new KnowledgeContentIndex();
        state.setId(9L);
        state.setRunKey(run);
        when(states.begin(7L, 3L, key, "model", 2, "collection", endpoint)).thenReturn(state);
        when(vectors.client()).thenReturn(client);
        when(states.renew(eq(9L), eq(run), anyInt())).thenReturn(true);
        when(states.complete(9L, run, 1)).thenReturn(true);
        var chunk = new KnowledgeContentChunk();
        chunk.setChunkKey("chunk");
        chunk.setContent("text");
        when(chunks.listForBundle(3L)).thenReturn(List.of(chunk));
        when(invocation.embed(any(), any(), any())).thenReturn(new EmbeddingResult(List.of(List.of(0.1, 0.2)), "model", null));
    }

    @Test void indexesWithAuditedRunAndOnlyCompletesAfterUpsert() {
        assertEquals("READY", service.index(7L, 3L).status());
        var order = inOrder(client, invocation, states);
        order.verify(invocation).embed(argThat(context -> context.runId().toString().equals(run)), any(), any());
        order.verify(client).upsert(eq(3L), eq(key), any());
        order.verify(states).complete(9L, run, 1);
        verify(client).close();
    }

    @Test void rejectsWithdrawnRunBeforeWritingAnyVectors() {
        when(states.renew(9L, run, 0)).thenReturn(true, false);
        assertThrows(BusinessException.class, () -> service.index(7L, 3L));
        verify(client, never()).upsert(anyLong(), anyString(), any());
        verify(states).fail(9L, run);
    }

    @Test void upstreamFailureNeverMarksIndexReady() {
        when(invocation.embed(any(), any(), any())).thenThrow(new IllegalStateException("safe failure"));
        assertThrows(IllegalStateException.class, () -> service.index(7L, 3L));
        verify(states).fail(9L, run);
        verify(states, never()).complete(anyLong(), anyString(), anyInt());
    }
}
