package com.learnplatform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 缓存配置
 * 当 CACHE_TYPE=redis 时使用 Redis 缓存，否则使用内存缓存（本地开发友好）
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheTtlProperties.class)
public class RedisConfig {

    private static final String[] CACHE_NAMES = {
            "statistics",
            "adminStatistics",
            "dailyTrend",
            "courseStats",
            "learningReport",
            "learningPath",
            "knowledgeGraph",
            "learningDiagnosis",
            "submissionQuality",
            "submissionKPTagging",
            "submissionDifficulty",
            "questionReviewSuggestion",
            "globalSearch"
    };

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
                                          CacheTtlProperties cacheTtlProperties) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(cacheTtlProperties.getDefaultTtl())
                .prefixCacheNameWith("v2::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(cacheValueSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(
                        connectionFactory, BatchStrategies.scan(1000)))
                .transactionAware()
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("statistics",
                        cacheConfig(defaultConfig, cacheTtlProperties, "statistics", 5))
                .withCacheConfiguration("adminStatistics",
                        cacheConfig(defaultConfig, cacheTtlProperties, "adminStatistics", 3))
                .withCacheConfiguration("dailyTrend",
                        cacheConfig(defaultConfig, cacheTtlProperties, "dailyTrend", 5))
                .withCacheConfiguration("courseStats",
                        cacheConfig(defaultConfig, cacheTtlProperties, "courseStats", 5))
                .withCacheConfiguration("learningReport",
                        cacheConfig(defaultConfig, cacheTtlProperties, "learningReport", 10))
                .withCacheConfiguration("learningPath",
                        cacheConfig(defaultConfig, cacheTtlProperties, "learningPath", 10))
                .withCacheConfiguration("knowledgeGraph",
                        cacheConfig(defaultConfig, cacheTtlProperties, "knowledgeGraph", 10))
                .withCacheConfiguration("learningDiagnosis",
                        cacheConfig(defaultConfig, cacheTtlProperties, "learningDiagnosis", 10))
                .withCacheConfiguration("submissionQuality",
                        cacheConfig(defaultConfig, cacheTtlProperties, "submissionQuality", 30))
                .withCacheConfiguration("submissionKPTagging",
                        cacheConfig(defaultConfig, cacheTtlProperties, "submissionKPTagging", 30))
                .withCacheConfiguration("submissionDifficulty",
                        cacheConfig(defaultConfig, cacheTtlProperties, "submissionDifficulty", 30))
                .withCacheConfiguration("questionReviewSuggestion",
                        cacheConfig(defaultConfig, cacheTtlProperties, "questionReviewSuggestion", 30))
                .withCacheConfiguration("globalSearch",
                        cacheConfig(defaultConfig, cacheTtlProperties, "globalSearch", 5))
                .build();
    }

    static RedisSerializer<Object> cacheValueSerializer() {
        var validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.learnplatform.")
                .allowIfSubType(Pattern.compile(
                        "java\\.lang\\.(String|Boolean|Byte|Short|Integer|Long|Float|Double|Character)"))
                .allowIfSubType(Pattern.compile("java\\.math\\.(BigDecimal|BigInteger)"))
                .allowIfSubType(Pattern.compile("java\\.time\\.(LocalDate|LocalDateTime)"))
                .allowIfSubType(Pattern.compile("java\\.util\\.(ArrayList|LinkedList|HashMap|LinkedHashMap"
                        + "|HashSet|LinkedHashSet|TreeMap|TreeSet"
                        + "|Arrays\\$ArrayList|Collections\\$(EmptyList|EmptyMap|EmptySet|SingletonList|SingletonMap|SingletonSet"
                        + "|UnmodifiableCollection|UnmodifiableList|UnmodifiableRandomAccessList|UnmodifiableMap|UnmodifiableSet)"
                        + "|ImmutableCollections\\$(List12|ListN|Map1|MapN|Set12|SetN))"))
                .build();
        return GenericJacksonJsonRedisSerializer.builder(JsonMapper::builderWithJackson2Defaults)
                .enableDefaultTyping(validator)
                // Resolve the root as Object so its untrusted type hint also goes through the validator.
                .reader((mapper, source, type) -> mapper.readValue(source, Object.class))
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
        return new ConcurrentMapCacheManager(CACHE_NAMES) {
            @Override protected Cache createConcurrentMapCache(String name) {
                // Simple caches have no TTL; global statistics must stay fresh without per-answer full clears.
                return "adminStatistics".equals(name) ? new NoOpCache(name) : super.createConcurrentMapCache(name);
            }
        };
    }
}
