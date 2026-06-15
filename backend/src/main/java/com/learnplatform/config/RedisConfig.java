package com.learnplatform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
public class RedisConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("statistics", defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("adminStatistics", defaultConfig.entryTtl(Duration.ofMinutes(3)))
                .withCacheConfiguration("dailyTrend", defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("courseStats", defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("learningReport", defaultConfig.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("learningPath", defaultConfig.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("knowledgeGraph", defaultConfig.entryTtl(Duration.ofMinutes(10)))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "simple", matchIfMissing = true)
    public CacheManager simpleCacheManager() {
        return new SimpleCacheManager();
    }
}
