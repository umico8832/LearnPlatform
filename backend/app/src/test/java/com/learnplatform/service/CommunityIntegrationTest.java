package com.learnplatform.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.IntegrationTestBase;
import com.learnplatform.security.CustomUserDetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@AutoConfigureMockMvc
class CommunityIntegrationTest extends IntegrationTestBase {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private UsernamePasswordAuthenticationToken author;
    private UsernamePasswordAuthenticationToken other;
    private UsernamePasswordAuthenticationToken admin;

    @BeforeEach
    void prepare() {
        author = actor("USER");
        other = actor("USER");
        admin = actor("ADMIN");
    }

    @Test
    void sharedTopicReplyAndIdempotentLikesRespectOwnership() throws Exception {
        long id = create("TOPIC", false);
        mvc.perform(get("/api/community/posts/" + id).with(authentication(other)))
                .andExpect(jsonPath("$.data.title").value("社区集成测试"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/community/posts/" + id + "/like").with(authentication(other)))
                    .andExpect(jsonPath("$.code").value(0));
        }
        mvc.perform(get("/api/community/posts/" + id).with(authentication(other)))
                .andExpect(jsonPath("$.data.likeCount").value(1));
        String response =
                mvc.perform(
                                post("/api/community/posts/" + id + "/comments")
                                        .with(authentication(other))
                                        .contentType("application/json")
                                        .content("{\"body\":\"共同讨论\"}"))
                        .andExpect(jsonPath("$.code").value(0))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long comment = json.readTree(response).path("data").asLong();
        mvc.perform(delete("/api/community/comments/" + comment).with(authentication(author)))
                .andExpect(jsonPath("$.code").value(1003));
        mvc.perform(delete("/api/community/posts/" + id).with(authentication(other)))
                .andExpect(jsonPath("$.code").value(1003));
        mvc.perform(delete("/api/community/posts/" + id).with(authentication(author)))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/community/posts/" + id).with(authentication(other)))
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void pendingFilesArePrivateUntilAdministratorApproves() throws Exception {
        long id = create("question_bank", true);
        mvc.perform(get("/api/community/posts/" + id).with(authentication(other)))
                .andExpect(jsonPath("$.code").value(1004));
        String detail =
                mvc.perform(get("/api/community/posts/" + id).with(authentication(author)))
                        .andExpect(jsonPath("$.data.status").value("PENDING"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long file =
                json.readTree(detail).path("data").path("attachments").get(0).path("id").asLong();
        mvc.perform(get("/api/community/attachments/" + file).with(authentication(other)))
                .andExpect(jsonPath("$.code").value(1004));
        mvc.perform(
                        post("/api/admin/community/posts/" + id + "/review")
                                .with(authentication(other))
                                .contentType("application/json")
                                .content("{\"decision\":\"APPROVED\",\"note\":\"来源核验\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/admin/community/posts/" + id + "/review")
                                .with(authentication(admin))
                                .contentType("application/json")
                                .content("{\"decision\":\"APPROVED\",\"note\":\"来源核验\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/community/attachments/" + file).with(authentication(other)))
                .andExpect(content().bytes("original source".getBytes(StandardCharsets.UTF_8)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM community_question_link WHERE post_id=?",
                        Integer.class,
                        id));
    }

    @Test
    void foreignReplyAndUnavailableSubjectAreRejected() throws Exception {
        long first = create("TOPIC", false);
        long second = create("TOPIC", false);
        String response =
                mvc.perform(
                                post("/api/community/posts/" + first + "/comments")
                                        .with(authentication(author))
                                        .contentType("application/json")
                                        .content("{\"body\":\"first\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long comment = json.readTree(response).path("data").asLong();
        mvc.perform(
                        post("/api/community/posts/" + second + "/comments")
                                .with(authentication(other))
                                .contentType("application/json")
                                .content("{\"body\":\"foreign\",\"parentId\":" + comment + "}"))
                .andExpect(jsonPath("$.code").value(1001));
        mvc.perform(
                        put("/api/community/posts/" + second + "/comments/" + comment + "/like")
                                .with(authentication(other)))
                .andExpect(jsonPath("$.code").value(1004));
        mvc.perform(
                        multipart("/api/community/posts")
                                .file(
                                        new MockMultipartFile(
                                                "post",
                                                "",
                                                "application/json",
                                                "{\"title\":\"bad\",\"body\":\"bad\",\"contentType\":\"TOPIC\",\"subjectId\":\"not-real\"}"
                                                        .getBytes(StandardCharsets.UTF_8)))
                                .with(authentication(author)))
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void withdrawalRevokesFileAccessAndReviewerIdentityIsCheckedInDatabase() throws Exception {
        long id = create("question_bank", true);
        review(id, "APPROVED");
        long file =
                jdbc.queryForObject(
                        "SELECT id FROM community_attachment WHERE post_id=?", Long.class, id);
        review(id, "HIDDEN");
        mvc.perform(get("/api/community/attachments/" + file).with(authentication(other)))
                .andExpect(jsonPath("$.code").value(1004));
        mvc.perform(get("/api/community/posts/" + id + "/reviews").with(authentication(author)))
                .andExpect(jsonPath("$.data.length()").value(2));
        jdbc.update(
                "UPDATE user SET role='USER' WHERE id=?",
                ((CustomUserDetails) admin.getPrincipal()).getUserId());
        mvc.perform(
                        post("/api/admin/community/posts/" + id + "/review")
                                .with(authentication(admin))
                                .contentType("application/json")
                                .content("{\"decision\":\"APPROVED\",\"note\":\"stale role\"}"))
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void invalidAttachmentRollsBackAndQuotaIsServerControlled() throws Exception {
        long owner = ((CustomUserDetails) author.getPrincipal()).getUserId();
        String body =
                "{\"title\":\"bad"
                    + " attachment\",\"body\":\"body\",\"contentType\":\"question_bank\",\"subjectId\":\"data-structures\",\"sourceNote\":\"original\"}";
        mvc.perform(
                        multipart("/api/community/posts")
                                .file(
                                        new MockMultipartFile(
                                                "post",
                                                "",
                                                "application/json",
                                                body.getBytes(StandardCharsets.UTF_8)))
                                .file(
                                        new MockMultipartFile(
                                                "files",
                                                "../notes.txt",
                                                "text/plain",
                                                new byte[] {1}))
                                .with(authentication(author)))
                .andExpect(jsonPath("$.code").value(1001));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM community_post WHERE user_id=?",
                        Integer.class,
                        owner));
        long id = create("question_bank", true);
        jdbc.update(
                "UPDATE community_attachment SET size_bytes=? WHERE post_id=?",
                240L * 1024 * 1024,
                id);
        mvc.perform(
                        multipart("/api/community/posts")
                                .file(
                                        new MockMultipartFile(
                                                "post",
                                                "",
                                                "application/json",
                                                body.getBytes(StandardCharsets.UTF_8)))
                                .file(
                                        new MockMultipartFile(
                                                "files", "notes.txt", "text/plain", new byte[] {1}))
                                .with(authentication(author)))
                .andExpect(jsonPath("$.code").value(1001));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM community_post WHERE user_id=?",
                        Integer.class,
                        owner));
    }

    @Test
    void reviewedBankUsesExistingSubmissionWorkflowAndRetryDoesNotDuplicateQuestion()
            throws Exception {
        long id = create("question_bank", true);
        long course =
                jdbc.queryForObject(
                        "SELECT id FROM course WHERE status=1 AND deleted=0 LIMIT 1", Long.class);
        String body =
                "{\"content\":\"原创集成测试题\",\"questionType\":\"SHORT_ANSWER\",\"courseId\":"
                        + course
                        + ",\"difficulty\":2,\"correctAnswer\":\"后进先出\"}";
        String key = UUID.randomUUID().toString();
        mvc.perform(
                        post("/api/admin/community/posts/" + id + "/questions")
                                .with(authentication(admin))
                                .header("Idempotency-Key", key)
                                .contentType("application/json")
                                .content(body))
                .andExpect(jsonPath("$.code").value(1001));
        review(id, "APPROVED");
        String result =
                mvc.perform(
                                post("/api/admin/community/posts/" + id + "/questions")
                                        .with(authentication(admin))
                                        .header("Idempotency-Key", key)
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(jsonPath("$.code").value(0))
                        .andExpect(jsonPath("$.data.status").value(0))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long submission = json.readTree(result).path("data").path("id").asLong();
        mvc.perform(
                        post("/api/admin/community/posts/" + id + "/questions")
                                .with(authentication(admin))
                                .header("Idempotency-Key", key)
                                .contentType("application/json")
                                .content(body))
                .andExpect(jsonPath("$.data.id").value(submission));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM community_question_link WHERE post_id=?",
                        Integer.class,
                        id));
        mvc.perform(
                        post("/api/admin/submission/" + submission + "/review")
                                .with(authentication(admin))
                                .contentType("application/json")
                                .content("{\"status\":1,\"reviewComment\":\"原创核对\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(
                        post("/api/admin/submission/" + submission + "/import")
                                .with(authentication(admin)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(3));
        mvc.perform(get("/api/community/posts/" + id).with(authentication(other)))
                .andExpect(jsonPath("$.data.questionLinks[0].status").value(3));
    }

    @Test
    void concurrentLikesRemainUnique() throws Exception {
        long id = create("TOPIC", false);
        var a = java.util.concurrent.CompletableFuture.runAsync(() -> putLike(id));
        var b = java.util.concurrent.CompletableFuture.runAsync(() -> putLike(id));
        a.join();
        b.join();
        mvc.perform(get("/api/community/posts/" + id).with(authentication(other)))
                .andExpect(jsonPath("$.data.likeCount").value(1));
    }

    private void putLike(long id) {
        try {
            mvc.perform(put("/api/community/posts/" + id + "/like").with(authentication(other)))
                    .andExpect(jsonPath("$.code").value(0));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void review(long id, String decision) throws Exception {
        mvc.perform(
                        post("/api/admin/community/posts/" + id + "/review")
                                .with(authentication(admin))
                                .contentType("application/json")
                                .content("{\"decision\":\"" + decision + "\",\"note\":\"来源核验\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    private long create(String type, boolean file) throws Exception {
        String body =
                "{\"title\":\"社区集成测试\",\"body\":\"讨论真实内容\",\"contentType\":\""
                        + type
                        + "\",\"subjectId\":\"data-structures\",\"sourceNote\":\"原创内容\",\"conceptName\":\"栈\"}";
        var request =
                multipart("/api/community/posts")
                        .file(
                                new MockMultipartFile(
                                        "post",
                                        "",
                                        "application/json",
                                        body.getBytes(StandardCharsets.UTF_8)));
        if (file)
            request.file(
                    new MockMultipartFile(
                            "files",
                            "notes.txt",
                            "text/plain",
                            "original source".getBytes(StandardCharsets.UTF_8)));
        String result =
                mvc.perform(request.with(authentication(author)))
                        .andExpect(jsonPath("$.code").value(0))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(result).path("data").asLong();
    }

    private UsernamePasswordAuthenticationToken actor(String role) {
        String name = "community-" + UUID.randomUUID().toString().substring(0, 16);
        jdbc.update(
                "INSERT INTO user (username, password, role, status) VALUES (?, 'test-only', ?, 1)",
                name,
                role);
        Long id = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, name);
        return new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(id, name, role),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
