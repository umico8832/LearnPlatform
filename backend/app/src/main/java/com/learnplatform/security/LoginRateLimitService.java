package com.learnplatform.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 登录接口 IP 级限流服务。
 * <p>
 * 同一 IP 在 {@code windowSeconds} 秒内连续失败超过 {@code maxAttempts} 次后
 * 将被拒绝访问登录接口，直到窗口过期自动解除。
 * <p>
 * 使用 ConcurrentHashMap 实现，适合单实例部署；多实例部署时建议改用 Redis。
 */
@Service
public class LoginRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitService.class);

    /** 默认最大失败次数 */
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    /** 默认窗口时长（秒） */
    private static final long DEFAULT_WINDOW_SECONDS = 900;

    private final int maxAttempts;
    private final long windowSeconds;
    private final ConcurrentMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public LoginRateLimitService() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW_SECONDS);
    }

    /** 测试友好构造函数。 */
    public LoginRateLimitService(int maxAttempts, long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    public boolean isBlocked(String ip) {
        AttemptRecord record = attempts.get(ip);
        if (record == null) {
            return false;
        }
        if (Instant.now().isAfter(record.windowExpiry)) {
            attempts.remove(ip);
            return false;
        }
        return record.count >= maxAttempts;
    }

    public void recordFailure(String ip) {
        AttemptRecord record = attempts.compute(ip, (key, existing) -> {
            Instant now = Instant.now();
            if (existing == null || now.isAfter(existing.windowExpiry)) {
                return new AttemptRecord(1, now.plusSeconds(windowSeconds));
            }
            return new AttemptRecord(existing.count + 1, existing.windowExpiry);
        });
        if (record.count >= maxAttempts) {
            log.warn("IP {} 登录失败 {} 次，已被限流至 {}", ip, record.count, record.windowExpiry);
        }
    }

    public void clearRecord(String ip) {
        attempts.remove(ip);
    }

    public long getRemainingBlockSeconds(String ip) {
        AttemptRecord record = attempts.get(ip);
        if (record == null || record.count < maxAttempts) {
            return 0;
        }
        long remaining = Instant.now().until(record.windowExpiry, java.time.temporal.ChronoUnit.SECONDS);
        return Math.max(remaining, 0);
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> now.isAfter(entry.getValue().windowExpiry));
    }

    static class AttemptRecord {
        final int count;
        final Instant windowExpiry;

        AttemptRecord(int count, Instant windowExpiry) {
            this.count = count;
            this.windowExpiry = windowExpiry;
        }
    }
}
