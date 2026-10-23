package com.learnplatform.config;

import com.learnplatform.service.CacheEvictService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class RedisCacheScanIntegrationTest {
    @Test void administrativeRegionClearUsesScanAndPreservesOtherNamespaces() throws Exception {
        try (var redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)) {
            redis.start();
            var factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            factory.afterPropertiesSet();
            factory.start();
            try {
                var manager = new RedisConfig().redisCacheManager(factory, new CacheTtlProperties());
                ((org.springframework.data.redis.cache.RedisCacheManager) manager).afterPropertiesSet();
                var region = manager.getCache("adminStatistics");
                for (int i = 0; i < 2500; i++) { region.put("fixture-" + i, "value"); }
                manager.getCache("statistics").put(987405L, "unaffected");
                try (var connection = factory.getConnection()) {
                    Properties before = connection.serverCommands().info("commandstats");
                    new CacheEvictService(manager).evictAdminStatistics();
                    Properties after = connection.serverCommands().info("commandstats");
                    assertTrue(calls(after, "scan") > calls(before, "scan"));
                    assertEquals(calls(before, "keys"), calls(after, "keys"));
                    assertNull(region.get("fixture-0"));
                    assertNull(region.get("fixture-2499"));
                    assertNotNull(manager.getCache("statistics").get(987405L));
                    var evidence = Path.of("target", "performance", "redis-scan.txt");
                    Files.createDirectories(evidence.getParent());
                    Files.writeString(evidence, "Fixture: 2500 region keys, 1 unrelated key\nSCAN calls="
                            + (calls(after, "scan") - calls(before, "scan")) + "\nKEYS calls="
                            + (calls(after, "keys") - calls(before, "keys")) + "\n");
                }
            } finally { factory.destroy(); }
        }
    }

    private long calls(Properties stats, String command) {
        String value = stats.getProperty("cmdstat_" + command, "calls=0");
        return Long.parseLong(value.substring("calls=".length()).split(",")[0]);
    }
}
