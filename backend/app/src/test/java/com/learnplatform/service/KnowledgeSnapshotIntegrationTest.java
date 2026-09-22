package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.config.KnowledgeConfig;
import com.learnplatform.dto.KnowledgeImportVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.knowledge.KnowledgeSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@AutoConfigureMockMvc
class KnowledgeSnapshotIntegrationTest extends IntegrationTestBase {
    @Autowired private KnowledgeSnapshotStoreService store;
    @Autowired private KnowledgeIndexStateService indexes;
    @Autowired private KnowledgeConfig config;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    private Long actor;
    private String course;

    @BeforeEach void prepare() {
        String username = "snapshot-" + UUID.randomUUID().toString().substring(0, 20);
        jdbc.update("INSERT INTO user (username, password, role, status) VALUES (?, 'test-only', 'ADMIN', 1)", username);
        actor = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
        course = "course-" + UUID.randomUUID();
    }

    @Test void concurrentImportIsAtomicAndIdempotent() {
        var snapshot = snapshot("a".repeat(64), "chunk");
        var first = CompletableFuture.supplyAsync(() -> store.store(actor, snapshot));
        var second = CompletableFuture.supplyAsync(() -> store.store(actor, snapshot));
        KnowledgeImportVO a = first.join();
        KnowledgeImportVO b = second.join();
        assertEquals(a.bundleId(), b.bundleId());
        assertNotEquals(a.alreadyImported(), b.alreadyImported());
        assertEquals("PENDING", a.reviewStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_content_chunk WHERE bundle_id=?",
                Integer.class, a.bundleId()));
        assertThrows(BusinessException.class, () -> store.store(actor, snapshot("b".repeat(64), "chunk")));
        assertEquals("a".repeat(64), jdbc.queryForObject(
                "SELECT manifest_hash FROM knowledge_content_bundle WHERE id=?", String.class, a.bundleId()));
    }

    @Test void chunkFailureRollsBackEntireBundle() {
        var snapshot = snapshot("a".repeat(64), "chunk");
        var duplicate = new KnowledgeSnapshot(course, "v1", snapshot.manifestHash(), "revision", "reviewed",
                List.of(snapshot.chunks().getFirst(), snapshot.chunks().getFirst()));
        assertThrows(RuntimeException.class, () -> store.store(actor, duplicate));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_content_bundle WHERE course_key=?",
                Integer.class, course));
    }

    @Test void administratorImportsConfiguredRealSnapshotThroughHttpAndLearnerIsDenied() throws Exception {
        config.setSnapshotPath(Path.of("../../content/knowledge/cs408/v1").toAbsolutePath().normalize().toString());
        var admin = new UsernamePasswordAuthenticationToken(new CustomUserDetails(actor, "admin", "ADMIN"), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        mvc.perform(post("/api/admin/knowledge/import").with(authentication(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.chunkCount").value(556));
        var learner = new UsernamePasswordAuthenticationToken(new CustomUserDetails(actor, "user", "USER"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        mvc.perform(post("/api/admin/knowledge/import").with(authentication(learner)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/knowledge/import")).andExpect(status().isUnauthorized());
    }

    @Test void administratorCanReadPendingBundleSourceThroughKnowledgeQuery() throws Exception {
        Long bundleId = store.store(actor, snapshot("a".repeat(64), "pending-source")).bundleId();
        var admin = administrator();

        mvc.perform(get("/api/admin/knowledge")
                        .param("courseKey", course).param("reviewStatus", "PENDING")
                        .param("pageNum", "1").param("pageSize", "20").with(authentication(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].bundleId").value(bundleId))
                .andExpect(jsonPath("$.data.records[0].courseKey").value(course))
                .andExpect(jsonPath("$.data.records[0].manifestHash").value("a".repeat(64)))
                .andExpect(jsonPath("$.data.records[0].sourceRevision").value("revision"))
                .andExpect(jsonPath("$.data.records[0].sourceQualityStatus").value("reviewed"))
                .andExpect(jsonPath("$.data.records[0].reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.records[0].chunkCount").value(1))
                .andExpect(jsonPath("$.data.records[0].importedBy").value(actor))
                .andExpect(jsonPath("$.data.records[0].importedAt").exists());
        mvc.perform(get("/api/admin/knowledge/{bundleId}", bundleId).with(authentication(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bundleId").value(bundleId))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"));
        mvc.perform(get("/api/admin/knowledge/{bundleId}/chunks", bundleId)
                        .param("pageNum", "1").param("pageSize", "20").with(authentication(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].bundleId").value(bundleId))
                .andExpect(jsonPath("$.data.records[0].chunkId").value("pending-source"))
                .andExpect(jsonPath("$.data.records[0].text").value("content"))
                .andExpect(jsonPath("$.data.records[0].contentHash").value("c".repeat(64)))
                .andExpect(jsonPath("$.data.records[0].metadataJson").value("{}"));
    }

    @Test void administratorQuerySeparatesVersionsAndConceptsWithStablePages() throws Exception {
        Long v1 = store.store(actor, snapshot("v1", "a".repeat(64), List.of(
                chunk("v1-first", "concept-a", "v1 first"), chunk("v1-second", "concept-b", "v1 second")))).bundleId();
        Long v2 = store.store(actor, snapshot("v2", "b".repeat(64), List.of(
                chunk("v2-only", "concept-a", "v2 only")))).bundleId();
        var admin = administrator();

        mvc.perform(get("/api/admin/knowledge").param("courseKey", course)
                        .param("pageNum", "1").param("pageSize", "1").with(authentication(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].bundleId").value(v2));
        mvc.perform(get("/api/admin/knowledge").param("courseKey", course)
                        .param("pageNum", "2").param("pageSize", "1").with(authentication(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].bundleId").value(v1));
        mvc.perform(get("/api/admin/knowledge/{bundleId}/chunks", v1)
                        .param("conceptId", "concept-b").param("pageNum", "1").param("pageSize", "20")
                        .with(authentication(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].chunkId").value("v1-second"))
                .andExpect(jsonPath("$.data.records[0].text").value("v1 second"));
        mvc.perform(get("/api/admin/knowledge/{bundleId}/chunks", v2)
                        .param("pageNum", "1").param("pageSize", "20").with(authentication(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].chunkId").value("v2-only"));
    }

    @Test void administratorQueryReturnsReviewMetadataAndSafeIndexFields() throws Exception {
        Long bundleId = store.store(actor, snapshot("a".repeat(64), "reviewed-source")).bundleId();
        indexes.review(actor, bundleId, "REVIEWED", "人工确认来源与内容");
        indexes.begin(actor, bundleId, "d".repeat(64), "embedding-test", 2, "knowledge-test", "e".repeat(64));
        var admin = administrator();

        mvc.perform(get("/api/admin/knowledge/{bundleId}", bundleId).with(authentication(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.data.reviewedBy").value(actor))
                .andExpect(jsonPath("$.data.reviewedAt").exists())
                .andExpect(jsonPath("$.data.reviewNote").value("人工确认来源与内容"));
        mvc.perform(get("/api/admin/knowledge/{bundleId}/indexes", bundleId)
                        .param("pageNum", "1").param("pageSize", "20").with(authentication(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].bundleId").value(bundleId))
                .andExpect(jsonPath("$.data.records[0].indexKey").value("d".repeat(64)))
                .andExpect(jsonPath("$.data.records[0].model").value("embedding-test"))
                .andExpect(jsonPath("$.data.records[0].dimensions").value(2))
                .andExpect(jsonPath("$.data.records[0].status").value("INDEXING"))
                .andExpect(jsonPath("$.data.records[0].indexedCount").value(0))
                .andExpect(jsonPath("$.data.records[0].leaseUntil").exists())
                .andExpect(jsonPath("$.data.records[0].runKey").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].vectorEndpointHash").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].collectionName").doesNotExist());
    }

    @Test void knowledgeQueryRejectsUnauthenticatedAndNonAdministratorRequestsAndInvalidTargets() throws Exception {
        Long learner = createUser("USER");
        var learnerAuthentication = authentication(new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(learner, "learner", "USER"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mvc.perform(get("/api/admin/knowledge")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/knowledge").with(learnerAuthentication)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/knowledge/{bundleId}", 999999L).with(authentication(administrator())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1004));
        mvc.perform(get("/api/admin/knowledge").param("pageNum", "0").with(authentication(administrator())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1001));
        mvc.perform(get("/api/admin/knowledge").param("pageSize", "51").with(authentication(administrator())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1001));
        mvc.perform(get("/api/admin/knowledge/{bundleId}/chunks", 999999L)
                        .param("pageNum", "1").param("pageSize", "0").with(authentication(administrator())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1001));
        mvc.perform(get("/api/admin/knowledge/{bundleId}/indexes", 999999L)
                        .param("pageNum", "0").param("pageSize", "20").with(authentication(administrator())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1001));
    }

    @Test void reviewAndFencingProtectReadyIndexAndWithdrawal() {
        Long bundleId = store.store(actor, snapshot("a".repeat(64), "chunk")).bundleId();
        String indexKey = "d".repeat(64);
        String endpoint = "e".repeat(64);
        assertThrows(BusinessException.class, () -> indexes.begin(actor, bundleId, indexKey, "model", 2, "test", endpoint));
        indexes.review(actor, bundleId, "REVIEWED", "已逐条核对测试内容");
        var first = indexes.begin(actor, bundleId, indexKey, "model", 2, "test", endpoint);
        assertFalse(indexes.complete(first.getId(), first.getRunKey(), 0));
        assertFalse(indexes.renew(first.getId(), first.getRunKey(), 2));
        assertFalse(indexes.complete(first.getId(), UUID.randomUUID().toString(), 1));
        assertTrue(indexes.complete(first.getId(), first.getRunKey(), 1));
        assertNotNull(indexes.ready(bundleId, indexKey));
        indexes.markDeleted(actor, bundleId);
        assertNull(indexes.ready(bundleId, indexKey));
        assertFalse(indexes.complete(first.getId(), first.getRunKey(), 1));
        assertTrue(indexes.purged(first.getId()));
        assertTrue(indexes.markDeleted(actor, bundleId).isEmpty());
        assertThrows(BusinessException.class, () -> indexes.review(actor, bundleId, "REVIEWED", "不能恢复撤回版本"));
    }

    @Test void expiredLeaseCanBeReclaimedAndOldWorkerCannotComplete() {
        Long bundleId = store.store(actor, snapshot("a".repeat(64), "chunk")).bundleId();
        String key = "d".repeat(64);
        String endpoint = "e".repeat(64);
        indexes.review(actor, bundleId, "REVIEWED", "人工核对测试内容");
        var first = indexes.begin(actor, bundleId, key, "model", 2, "test", endpoint);
        assertThrows(BusinessException.class, () -> indexes.begin(actor, bundleId, key, "model", 2, "test", endpoint));
        jdbc.update("UPDATE knowledge_content_index SET lease_until=DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE id=?", first.getId());
        var second = indexes.begin(actor, bundleId, key, "model", 2, "test", endpoint);
        assertNotEquals(first.getRunKey(), second.getRunKey());
        assertFalse(indexes.complete(first.getId(), first.getRunKey(), 1));
        indexes.markDeleted(actor, bundleId);
        assertFalse(indexes.purged(second.getId()));
        assertFalse(indexes.renew(second.getId(), second.getRunKey(), 1));
    }

    private KnowledgeSnapshot snapshot(String hash, String id) {
        return snapshot("v1", hash, List.of(chunk(id, "concept", "content")));
    }

    private KnowledgeSnapshot snapshot(String version, String hash, List<KnowledgeSnapshot.Chunk> chunks) {
        return new KnowledgeSnapshot(course, version, hash, "revision", "reviewed", chunks);
    }

    private KnowledgeSnapshot.Chunk chunk(String id, String conceptId, String text) {
        return new KnowledgeSnapshot.Chunk(id, conceptId, "title", text, "c".repeat(64), "{}", "reviewed");
    }

    private UsernamePasswordAuthenticationToken administrator() {
        return new UsernamePasswordAuthenticationToken(new CustomUserDetails(actor, "admin", "ADMIN"), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Long createUser(String role) {
        String username = role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 20);
        jdbc.update("INSERT INTO user (username, password, role, status) VALUES (?, 'test-only', ?, 1)", username, role);
        return jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
    }
}
