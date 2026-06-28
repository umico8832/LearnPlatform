package com.learnplatform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * 缓存配置
 * 当 CACHE_TYPE=redis 时使用 Redis 缓存，否则使用内存缓存（本地开发友好）
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheTtlProperties.class)
public class RedisConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
                                          CacheTtlProperties cacheTtlProperties) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(cacheTtlProperties.getDefaultTtl())
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("statistics", cacheConfig(defaultConfig, cacheTtlProperties, "statistics", 5))
                .withCacheConfiguration("adminStatistics", cacheConfig(defaultConfig, cacheTtlProperties, "adminStatistics", 3))
                .withCacheConfiguration("dailyTrend", cacheConfig(defaultConfig, cacheTtlProperties, "dailyTrend", 5))
                .withCacheConfiguration("courseStats", cacheConfig(defaultConfig, cacheTtlProperties, "courseStats", 5))
                .withCacheConfiguration("learningReport", cacheConfig(defaultConfig, cacheTtlProperties, "learningReport", 10))
                .withCacheConfiguration("learningPath", cacheConfig(defaultConfig, cacheTtlProperties, "learningPath", 10))
                .withCacheConfiguration("knowledgeGraph", cacheConfig(defaultConfig, cacheTtlProperties, "knowledgeGraph", 10))
                .withCacheConfiguration("learningDiagnosis", cacheConfig(defaultConfig, cacheTtlProperties, "learningDiagnosis", 10))
                .withCacheConfiguration("submissionQuality", cacheConfig(defaultConfig, cacheTtlProperties, "submissionQuality", 30))
                .withCacheConfiguration("submissionKPTagging", cacheConfig(defaultConfig, cacheTtlProperties, "submissionKPTagging", 30))
                .withCacheConfiguration("submissionDifficulty", cacheConfig(defaultConfig, cacheTtlProperties, "submissionDifficulty", 30))
                .withCacheConfiguration("globalSearch", cacheConfig(defaultConfig, cacheTtlProperties, "globalSearch", 5))
                .build();
    }

    private RedisCacheConfiguration cacheConfig(RedisCacheConfiguration defaultConfig,
                                                CacheTtlProperties cacheTtlProperties,
                                                String cacheName,
                                                long fallbackMinutes) {
        Duration fallback = Duration.ofMinutes(fallbackMinutes);
        return defaultConfig.entryTtl(cacheTtlProperties.ttlOf(cacheName, fallback));
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "simple", matchIfMissing = true)
    public CacheManager simpleCacheManager() {
        return new SimpleCacheManager();
    }
}
