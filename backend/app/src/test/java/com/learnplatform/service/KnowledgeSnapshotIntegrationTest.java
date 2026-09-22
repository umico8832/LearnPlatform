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
        return new KnowledgeSnapshot(course, "v1", hash, "revision", "reviewed", List.of(
                new KnowledgeSnapshot.Chunk(id, "concept", "title", "content", "c".repeat(64), "{}", "reviewed")));
    }
}
