package com.learnplatform.config;

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
    private static final long DEFAULT_WINDOW_SECONDS = 900; // 15 分钟

    private final int maxAttempts;
    private final long windowSeconds;

    /**
     * IP -> 记录
     */
    private final ConcurrentMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public LoginRateLimitService() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW_SECONDS);
    }

    /** 测试友好构造函数 */
    public LoginRateLimitService(int maxAttempts, long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    /**
     * 判断给定 IP 是否被限流（已超过最大失败次数）。
     */
    public boolean isBlocked(String ip) {
        AttemptRecord record = attempts.get(ip);
        if (record == null) {
            return false;
        }
        // 窗口过期则清除
        if (Instant.now().isAfter(record.windowExpiry)) {
            attempts.remove(ip);
            return false;
        }
        return record.count >= maxAttempts;
    }

    /**
     * 记录一次失败的登录尝试。调用方应在登录失败时调用此方法。
     */
    public void recordFailure(String ip) {
        AttemptRecord record = attempts.compute(ip, (key, existing) -> {
            Instant now = Instant.now();
            if (existing == null || now.isAfter(existing.windowExpiry)) {
                // 新建或重置窗口
                return new AttemptRecord(1, now.plusSeconds(windowSeconds));
            }
            return new AttemptRecord(existing.count + 1, existing.windowExpiry);
        });
        if (record.count >= maxAttempts) {
            log.warn("IP {} 登录失败 {} 次，已被限流至 {}", ip, record.count, record.windowExpiry);
        }
    }

    /**
     * 登录成功后清除该 IP 的失败记录。
     */
    public void clearRecord(String ip) {
        attempts.remove(ip);
    }

    /**
     * 获取当前 IP 剩余封锁秒数，未被封锁返回 0。
     */
    public long getRemainingBlockSeconds(String ip) {
        AttemptRecord record = attempts.get(ip);
        if (record == null || record.count < maxAttempts) {
            return 0;
        }
        long remaining = Instant.now().until(record.windowExpiry, java.time.temporal.ChronoUnit.SECONDS);
        return Math.max(remaining, 0);
    }

    /**
     * 每 5 分钟清理过期记录，避免内存泄漏。
     */
    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> now.isAfter(entry.getValue().windowExpiry));
    }

    /**
     * 内部记录：累计次数 + 窗口过期时间。
     */
    static class AttemptRecord {
        final int count;
        final Instant windowExpiry;

        AttemptRecord(int count, Instant windowExpiry) {
            this.count = count;
            this.windowExpiry = windowExpiry;
        }
    }
}