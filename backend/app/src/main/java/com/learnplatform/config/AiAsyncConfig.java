package com.learnplatform.config;

import com.learnplatform.service.ai.AiTaskExecutor;
import com.learnplatform.service.ai.AiAdmissionGate;
import com.learnplatform.service.ai.LocalAiAdmissionGate;
import com.learnplatform.service.ai.RedisAiAdmissionGate;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AiExecutionProperties.class)
public class AiAsyncConfig {

    @Bean(name = "aiTaskExecutor")
    public AiTaskExecutor aiTaskExecutor(AiExecutionProperties properties, MeterRegistry meters,
                                         AiAdmissionGate admission) {
        return new AiTaskExecutor(properties, meters, admission);
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public AiAdmissionGate redisAiAdmissionGate(DataRedisProperties redisProperties, AiExecutionProperties properties) {
        Duration timeout = admissionTimeout(properties.lease());
        // AI admission intentionally supports the project's standalone Redis deployment only. It owns this
        // connection factory so a stalled cache command cannot delay a lease renewal or replace Boot's cache factory.
        var standalone = new RedisStandaloneConfiguration(redisProperties.getHost(), redisProperties.getPort());
        standalone.setDatabase(redisProperties.getDatabase());
        if (redisProperties.getUsername() != null && !redisProperties.getUsername().isBlank()) {
            standalone.setUsername(redisProperties.getUsername());
        }
        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
            standalone.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }
        var client = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .shutdownTimeout(Duration.ZERO)
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(timeout).build())
                        .build())
                .build();
        var factory = new LettuceConnectionFactory(standalone, client);
        factory.afterPropertiesSet();
        factory.start();
        var template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return new RedisAiAdmissionGate(template, properties.concurrency(), properties.lease(), factory::destroy);
    }

    static Duration admissionTimeout(Duration lease) {
        Duration eighth = lease.dividedBy(8);
        return eighth.compareTo(Duration.ofMillis(500)) < 0 ? eighth : Duration.ofMillis(500);
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "simple", matchIfMissing = true)
    public AiAdmissionGate localAiAdmissionGate(AiExecutionProperties properties) {
        return new LocalAiAdmissionGate(properties.concurrency());
    }
}
