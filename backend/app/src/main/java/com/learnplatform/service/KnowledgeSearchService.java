package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.KnowledgeConfig;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgeContentChunk;
import com.learnplatform.entity.UserCourse;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgeContentBundleMapper;
import com.learnplatform.mapper.KnowledgeContentChunkMapper;
import com.learnplatform.mapper.UserCourseMapper;
import com.learnplatform.service.ai.AiCallContext;
import com.learnplatform.service.knowledge.KnowledgeSearchResult;
import com.learnplatform.service.knowledge.vector.QdrantKnowledgeVectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeSearchService {
    private final KnowledgeVectorService vectors;
    private final KnowledgeIndexStateService states;
    private final KnowledgeContentBundleMapper bundles;
    private final KnowledgeContentChunkMapper chunks;
    private final AiInvocationService invocation;
    private final CourseMapper courses;
    private final UserCourseMapper userCourses;
    private final KnowledgeConfig config;

    public KnowledgeSearchService(KnowledgeVectorService vectors, KnowledgeIndexStateService states,
                                   KnowledgeContentBundleMapper bundles, KnowledgeContentChunkMapper chunks,
                                   AiInvocationService invocation, CourseMapper courses,
                                   UserCourseMapper userCourses, KnowledgeConfig config) {
        this.vectors = vectors;
        this.states = states;
        this.bundles = bundles;
        this.chunks = chunks;
        this.invocation = invocation;
        this.courses = courses;
        this.userCourses = userCourses;
        this.config = config;
    }

    public boolean enabled() {
        return config.isRetrievalEnabled();
    }

    public KnowledgeSearchResult search(Long userId, Long courseId, String query, UUID runId) {
        Course course = requireCourse(userId, courseId);
        if (!enabled()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "课程知识检索尚未启用");
        }
        if (query == null || query.isBlank() || query.length() > 1000
                || config.getTopK() <= 0 || config.getTopK() > 10) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "知识检索参数无效");
        }
        var bundle = course.getContentKey() == null ? null : bundles.latestReviewed(course.getContentKey());
        if (bundle == null) {
            return new KnowledgeSearchResult(List.of());
        }
        var space = vectors.space();
        if (states.ready(bundle.getId(), space.indexKey()) == null) {
            return new KnowledgeSearchResult(List.of());
        }
        var request = new EmbeddingRequest(space.model(), List.of(query), space.dimensions());
        var result = invocation.embed(new AiCallContext(userId, "tutor_knowledge_query", runId), request,
                new Cancellation());
        if (result.vectors().size() != 1 || result.model() != null && !result.model().equals(space.model())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "Embedding 模型与知识索引不一致");
        }
        try (var client = vectors.client()) {
            client.requireCollection(space.dimensions());
            var matches = client.search(bundle.getId(), space.indexKey(), result.vectors().getFirst(),
                    config.getTopK());
            if (matches.isEmpty()) {
                return new KnowledgeSearchResult(List.of());
            }
            var rows = chunks.selectList(new QueryWrapper<KnowledgeContentChunk>().eq("bundle_id", bundle.getId())
                    .in("chunk_key", matches.stream().map(QdrantKnowledgeVectorStore.Match::chunkId).toList()));
            var byKey = new HashMap<String, KnowledgeContentChunk>();
            rows.forEach(row -> byKey.put(row.getChunkKey(), row));
            var citations = new ArrayList<KnowledgeSearchResult.Citation>();
            for (var match : matches) {
                var row = byKey.get(match.chunkId());
                if (row == null) {
                    throw new IllegalStateException("Knowledge index does not match its content version");
                }
                citations.add(new KnowledgeSearchResult.Citation(bundle.getId(), bundle.getBundleVersion(),
                        row.getChunkKey(), row.getConceptKey(), row.getTitle(), row.getContent(),
                        row.getContentHash()));
            }
            Course current = requireCourse(userId, courseId);
            if (!course.getContentKey().equals(current.getContentKey())
                    || states.ready(bundle.getId(), space.indexKey()) == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "课程知识版本已撤回或变更");
            }
            return new KnowledgeSearchResult(citations);
        }
    }

    private Course requireCourse(Long userId, Long courseId) {
        Course course = courseId == null ? null : courses.selectById(courseId);
        if (userId == null || course == null || !Integer.valueOf(1).equals(course.getStatus())
                || userCourses.selectCount(new QueryWrapper<UserCourse>()
                .eq("user_id", userId).eq("course_id", courseId)) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程学习空间不存在");
        }
        return course;
    }
}
