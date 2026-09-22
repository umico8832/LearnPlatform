package com.learnplatform.service;

import com.learnplatform.ai.model.EmbeddingResult;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.config.KnowledgeConfig;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgeContentBundle;
import com.learnplatform.entity.KnowledgeContentChunk;
import com.learnplatform.entity.KnowledgeContentIndex;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgeContentBundleMapper;
import com.learnplatform.mapper.KnowledgeContentChunkMapper;
import com.learnplatform.mapper.UserCourseMapper;
import com.learnplatform.service.knowledge.vector.KnowledgeVectorSpace;
import com.learnplatform.service.knowledge.vector.QdrantKnowledgeVectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeSearchServiceTest {
    private final KnowledgeVectorService vectors = mock(KnowledgeVectorService.class);
    private final KnowledgeIndexStateService states = mock(KnowledgeIndexStateService.class);
    private final KnowledgeContentBundleMapper bundles = mock(KnowledgeContentBundleMapper.class);
    private final KnowledgeContentChunkMapper chunks = mock(KnowledgeContentChunkMapper.class);
    private final AiInvocationService invocation = mock(AiInvocationService.class);
    private final CourseMapper courses = mock(CourseMapper.class);
    private final UserCourseMapper enrollments = mock(UserCourseMapper.class);
    private final KnowledgeConfig config = new KnowledgeConfig();
    private final QdrantKnowledgeVectorStore client = mock(QdrantKnowledgeVectorStore.class);
    private final KnowledgeSearchService service = new KnowledgeSearchService(vectors, states, bundles, chunks,
            invocation, courses, enrollments, config);
    private final String key = "a".repeat(64);

    @Test void rejectsMissingEnrollmentBeforeAnyModelCall() {
        authorize();
        when(enrollments.selectCount(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () -> search());
        verifyNoInteractions(invocation, vectors, bundles);
    }

    @Test void absentReviewedContentDoesNotCallCloud() {
        authorize();
        assertTrue(search().citations().isEmpty());
        verifyNoInteractions(invocation, vectors);
    }

    @Test void onlyReturnsDatabaseContentFromCurrentVersion() {
        ready();
        var result = search();
        assertEquals("reviewed text", result.citations().getFirst().text());
        assertEquals("v1", result.citations().getFirst().version());
        verify(client).search(3L, key, List.of(0.1, 0.2), 4);
        verify(invocation).embed(argThat(context -> context.userId().equals(7L)
                && context.function().equals("tutor_knowledge_query")), any(), any());
    }

    @Test void withdrawalDuringSearchDoesNotExposeFetchedText() {
        ready();
        when(states.ready(3L, key)).thenReturn(new KnowledgeContentIndex(), null);
        assertThrows(BusinessException.class, () -> search());
    }

    private com.learnplatform.service.knowledge.KnowledgeSearchResult search() {
        return service.search(7L, 10L, "栈", UUID.randomUUID());
    }

    private void authorize() {
        config.setRetrievalEnabled(true);
        var course = new Course();
        course.setStatus(1);
        course.setContentKey("course");
        when(courses.selectById(10L)).thenReturn(course);
        when(enrollments.selectCount(any())).thenReturn(1L);
    }

    private void ready() {
        authorize();
        var bundle = new KnowledgeContentBundle();
        bundle.setId(3L);
        bundle.setBundleVersion("v1");
        when(bundles.latestReviewed("course")).thenReturn(bundle);
        when(vectors.space()).thenReturn(new KnowledgeVectorSpace(key, "model", 2, "collection", "b".repeat(64)));
        when(states.ready(3L, key)).thenReturn(new KnowledgeContentIndex());
        when(invocation.embed(any(), any(), any())).thenReturn(new EmbeddingResult(List.of(List.of(0.1, 0.2)), "model", null));
        when(vectors.client()).thenReturn(client);
        when(client.search(3L, key, List.of(0.1, 0.2), 4)).thenReturn(List.of(new QdrantKnowledgeVectorStore.Match("chunk", 0.9)));
        var row = new KnowledgeContentChunk();
        row.setChunkKey("chunk");
        row.setContent("reviewed text");
        when(chunks.selectList(any())).thenReturn(List.of(row));
    }
}
