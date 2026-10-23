package com.learnplatform.config;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.security.CustomUserDetails;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest(properties = "spring.cache.type=redis")
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@DirtiesContext
class RedisCacheResponseIntegrationTest extends IntegrationTestBase {
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    static { REDIS.start(); }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void stopRedis() { REDIS.stop(); }

    @Autowired MockMvc mvc;
    @Autowired CacheManager manager;
    @Autowired RedisConnectionFactory connections;
    @Autowired JdbcTemplate db;

    @Test
    void coldAndRedisHitHttpBodiesAreIdenticalWithNewYorkBusinessDates() throws Exception {
        assertThat(manager).isInstanceOf(RedisCacheManager.class);
        long user = 987406L;
        db.update("INSERT INTO user(id,username,password,role,status,deleted) VALUES (?,'redis-response','test','USER',1,0)", user);
        long question = db.queryForObject("SELECT MIN(question_id) FROM question_knowledge_point", Long.class);
        db.update("INSERT INTO practice_record(user_id,question_id,user_answer,is_correct,create_time) VALUES(?,?,'A',1,?)",
                user, question, com.learnplatform.common.time.BusinessTime.now().withNano(0));
        try {
            for (var entry : Map.of("statistics", "overview", "learningPath", "learning-path",
                    "knowledgeGraph", "knowledge-graph", "learningDiagnosis", "learning-diagnosis").entrySet()) {
                Object key = List.of("learningPath", "knowledgeGraph").contains(entry.getKey()) ? user + ":null" : user;
                var cache = manager.getCache(entry.getKey());
                cache.evictIfPresent(key);
                String cold = body(entry.getValue(), user);
                if (entry.getKey().equals("statistics")) {
                    assertThat(cold).contains("\"todayPractice\":1");
                }
                if (entry.getKey().equals("learningDiagnosis")) {
                    assertThat(cold).contains("\"activeDaysLast30\":1");
                }
                await().untilAsserted(() -> assertThat(cache.get(key)).as("Real Redis entry for %s", entry.getKey()).isNotNull());
                long hits = redisHits();
                String hit = body(entry.getValue(), user);
                assertThat(redisHits()).isGreaterThan(hits);
                assertThat(hit).as(entry.getKey()).isEqualTo(cold);
            }
        } finally {
            db.update("DELETE FROM practice_record WHERE user_id=?", user);
            db.update("DELETE FROM user WHERE id=?", user);
        }
    }

    @Test
    void legacyJackson2AndUnreadableUnversionedEntriesCannotBreakTheEndpoint() throws Exception {
        long user = 987407L;
        byte[] oldKey = ("statistics::" + user).getBytes(StandardCharsets.UTF_8);
        var old = new org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer();
        byte[] oldValue = old.serialize(RedisCacheFixtures.values().get("statistics"));
        try (var connection = connections.getConnection()) {
            connection.stringCommands().setEx(oldKey, 300, oldValue);
        }
        manager.getCache("statistics").evictIfPresent(user);
        String cold = body("overview", user);
        assertThat(body("overview", user)).isEqualTo(cold);
        try (var connection = connections.getConnection()) {
            assertThat(connection.stringCommands().get(oldKey)).isEqualTo(oldValue);
            assertThat(connection.keyCommands().ttl(oldKey)).isBetween(1L, 300L);
            assertThat(connection.keyCommands().exists(("v2::statistics::" + user).getBytes(StandardCharsets.UTF_8))).isTrue();
            connection.stringCommands().setEx(oldKey, 300, "unreadable legacy value".getBytes(StandardCharsets.UTF_8));
        }
        manager.getCache("statistics").evictIfPresent(user);
        assertThat(body("overview", user)).isEqualTo(cold);
    }

    private String body(String endpoint, long user) throws Exception {
        var actor = new UsernamePasswordAuthenticationToken(new CustomUserDetails(user, "redis-response", "USER"),
                null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return mvc.perform(get("/api/statistics/" + endpoint).with(authentication(actor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
    }

    private long redisHits() {
        try (var connection = connections.getConnection()) {
            return Long.parseLong(connection.serverCommands().info("stats").getProperty("keyspace_hits"));
        }
    }
}
