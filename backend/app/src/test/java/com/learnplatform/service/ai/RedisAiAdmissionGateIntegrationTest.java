package com.learnplatform.service.ai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.learnplatform.config.AiExecutionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class RedisAiAdmissionGateIntegrationTest {
    @Test void independentGatesShareLimitExpireDeadLeaseAndKeepNewerLeaseOnOldRelease() throws Exception {
        try (var redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)) {
            redis.start();
            var factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            factory.afterPropertiesSet();
            factory.start();
            try {
                var template = new StringRedisTemplate(factory);
                var firstInstance = new RedisAiAdmissionGate(template, 1, Duration.ofSeconds(1));
                var secondInstance = new RedisAiAdmissionGate(template, 1, Duration.ofSeconds(1));

                var expired = firstInstance.tryAcquire();
                assertNotNull(expired);
                assertNull(secondInstance.tryAcquire());
                Thread.sleep(1200);
                var recovered = secondInstance.tryAcquire();
                assertNotNull(recovered);
                expired.close();
                assertNull(firstInstance.tryAcquire(), "old token must not remove the newer instance lease");
                recovered.close();
            } finally { factory.destroy(); }
        }
    }

    @Test void renewalKeepsALongRunningLeaseUntilTheOwnerReleasesIt() throws Exception {
        try (var redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)) {
            redis.start();
            var factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            factory.afterPropertiesSet();
            factory.start();
            try {
                var template = new StringRedisTemplate(factory);
                var firstInstance = new RedisAiAdmissionGate(template, 1, Duration.ofSeconds(2));
                var secondInstance = new RedisAiAdmissionGate(template, 1, Duration.ofSeconds(2));
                var lease = firstInstance.tryAcquire();
                assertNotNull(lease);
                Thread.sleep(1200);
                assertTrue(lease.renew());
                Thread.sleep(1200);
                assertNull(secondInstance.tryAcquire(), "renewal must extend the owner lease");
                lease.close();
                assertNotNull(secondInstance.tryAcquire());
            } finally { factory.destroy(); }
        }
    }

    @Test void cancellingARealRedisBackedExecutorReturnsItsLeaseAfterWorkExits() throws Exception {
        try (var redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)) {
            redis.start();
            var factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            factory.afterPropertiesSet();
            factory.start();
            try {
                var template = new StringRedisTemplate(factory);
                var gate = new RedisAiAdmissionGate(template, 1, Duration.ofSeconds(3));
                var properties = new AiExecutionProperties(1, AiExecutionProperties.Mode.PLATFORM,
                        Duration.ofMillis(10), Duration.ofSeconds(3));
                try (var executor = new AiTaskExecutor(properties, new SimpleMeterRegistry(), gate)) {
                    var started = new CountDownLatch(1);
                    var interrupted = new CountDownLatch(1);
                    var reservation = executor.reserve();
                    reservation.execute(() -> {
                        started.countDown();
                        try { new CountDownLatch(1).await(); }
                        catch (InterruptedException exception) {
                            interrupted.countDown();
                            Thread.currentThread().interrupt();
                        }
                    });
                    assertTrue(started.await(1, TimeUnit.SECONDS));
                    reservation.cancel();
                    assertTrue(interrupted.await(2, TimeUnit.SECONDS));
                    var other = new RedisAiAdmissionGate(template, 1, Duration.ofSeconds(3));
                    AiAdmissionGate.Lease replacement = null;
                    for (int attempt = 0; attempt < 100 && replacement == null; attempt++) {
                        replacement = other.tryAcquire();
                        if (replacement == null) { Thread.sleep(10); }
                    }
                    assertNotNull(replacement);
                    replacement.close();
                }
            } finally { factory.destroy(); }
        }
    }

    @Test void oneSecondLeaseIsRenewedBeforeItCanExpire() throws Exception {
        try (var redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)) {
            redis.start();
            var factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            factory.afterPropertiesSet();
            factory.start();
            try {
                var template = new StringRedisTemplate(factory);
                var gate = new RedisAiAdmissionGate(template, 1, Duration.ofSeconds(1));
                var properties = new AiExecutionProperties(1, AiExecutionProperties.Mode.PLATFORM,
                        Duration.ofMillis(10), Duration.ofSeconds(1));
                try (var executor = new AiTaskExecutor(properties, new SimpleMeterRegistry(), gate);
                     var reservation = executor.reserve()) {
                    Thread.sleep(1200);
                    assertNull(new RedisAiAdmissionGate(template, 1, Duration.ofSeconds(1)).tryAcquire());
                }
            } finally { factory.destroy(); }
        }
    }

    @Test void shortLeaseCannotShortenTheRedisKeyPastAnotherInstancesLongLease() throws Exception {
        try (var redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)) {
            redis.start();
            var factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            factory.afterPropertiesSet();
            factory.start();
            try {
                var template = new StringRedisTemplate(factory);
                var longLeaseGate = new RedisAiAdmissionGate(template, 2, Duration.ofSeconds(5));
                var shortLeaseGate = new RedisAiAdmissionGate(template, 2, Duration.ofSeconds(1));
                var longLease = longLeaseGate.tryAcquire();
                var shortLease = shortLeaseGate.tryAcquire();
                assertNotNull(longLease);
                assertNotNull(shortLease);
                shortLease.close();
                Thread.sleep(1200);
                assertTrue(longLease.renew(), "short lease must not expire the long lease's Redis key");
                var secondLease = shortLeaseGate.tryAcquire();
                assertNotNull(secondLease);
                assertNull(longLeaseGate.tryAcquire(), "long and replacement leases still consume both slots");
                secondLease.close();
                longLease.close();
            } finally { factory.destroy(); }
        }
    }
}
