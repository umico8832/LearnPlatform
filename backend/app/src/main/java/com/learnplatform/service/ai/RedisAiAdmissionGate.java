package com.learnplatform.service.ai;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Redis-backed, fenced-by-token leases for the cross-instance AI budget. */
public final class RedisAiAdmissionGate implements AiAdmissionGate {
    static final String KEY = "learnplatform:ai:admission:leases";
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local now = redis.call('TIME')
            local nowMillis = now[1] * 1000 + math.floor(now[2] / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', nowMillis)
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[1]) then return 0 end
            redis.call('ZADD', KEYS[1], nowMillis + tonumber(ARGV[2]), ARGV[3])
            local latest = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES')[2]
            redis.call('PEXPIREAT', KEYS[1], math.floor(tonumber(latest)))
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
            local now = redis.call('TIME')
            local nowMillis = now[1] * 1000 + math.floor(now[2] / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', nowMillis)
            if not redis.call('ZSCORE', KEYS[1], ARGV[1]) then return 0 end
            redis.call('ZADD', KEYS[1], 'XX', nowMillis + tonumber(ARGV[2]), ARGV[1])
            local latest = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES')[2]
            redis.call('PEXPIREAT', KEYS[1], math.floor(tonumber(latest)))
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "return redis.call('ZREM', KEYS[1], ARGV[1])", Long.class);

    private final StringRedisTemplate redis;
    private final int limit;
    private final long leaseMillis;
    private final AutoCloseable connection;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RedisAiAdmissionGate(StringRedisTemplate redis, int limit, Duration lease) {
        this(redis, limit, lease, () -> { });
    }

    public RedisAiAdmissionGate(StringRedisTemplate redis, int limit, Duration lease, AutoCloseable connection) {
        this.redis = redis;
        this.limit = limit;
        this.leaseMillis = lease.toMillis();
        this.connection = connection;
    }

    @Override public Lease tryAcquire() {
        String token = UUID.randomUUID().toString();
        Long acquired = redis.execute(ACQUIRE, List.of(KEY), Integer.toString(limit), Long.toString(leaseMillis),
                token);
        return Long.valueOf(1).equals(acquired) ? new RedisLease(token) : null;
    }

    private final class RedisLease implements Lease {
        private final String token;
        private final AtomicBoolean released = new AtomicBoolean();

        private RedisLease(String token) { this.token = token; }

        @Override public boolean renew() {
            if (released.get()) { return false; }
            Long renewed = redis.execute(RENEW, List.of(KEY), token, Long.toString(leaseMillis));
            return Long.valueOf(1).equals(renewed);
        }

        @Override public void close() {
            if (released.compareAndSet(false, true)) { redis.execute(RELEASE, List.of(KEY), token); }
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) { return; }
        try { connection.close(); }
        catch (Exception exception) {
            throw new IllegalStateException("Cannot close AI admission Redis connection", exception);
        }
    }
}
