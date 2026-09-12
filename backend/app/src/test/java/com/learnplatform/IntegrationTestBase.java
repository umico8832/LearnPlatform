package com.learnplatform;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.io.File;

/**
 * 集成测试基类：使用 Testcontainers 提供真实 MySQL 实例。
 * 子类通过 {@code @SpringBootTest @ActiveProfiles("integration")} 继承即可。
 */
public abstract class IntegrationTestBase {

    static {
        // macOS Docker Desktop 默认 socket 路径
        if (System.getenv("DOCKER_HOST") == null) {
            String homeSocket = System.getProperty("user.home") + "/.docker/run/docker.sock";
            if (new File(homeSocket).exists()) {
                System.setProperty("DOCKER_HOST", "unix://" + homeSocket);
            }
        }
    }

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("learn_platform")
                    .withUsername("test")
                    .withPassword("test")
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.user", () -> "root");
        registry.add("spring.flyway.password", MYSQL::getPassword);
    }
}
