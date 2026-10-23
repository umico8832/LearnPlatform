package com.learnplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.SerializationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisCacheSerializerTest {
    @Test
    void rejectsUnapprovedRootAndNestedTypes() {
        var serializer = RedisConfig.cacheValueSerializer();
        for (String json : List.of("[\"java.io.File\",\"fixture\"]",
                "[\"java.util.ArrayList\",[[\"java.io.File\",\"fixture\"]]]",
                "{\"@class\":\"org.springframework.cache.support.NullValue\"}",
                "[\"java.util.concurrent.atomic.AtomicInteger\",1]")) {
            assertThatThrownBy(() -> serializer.deserialize(json.getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(SerializationException.class)
                    .rootCause().isInstanceOf(tools.jackson.databind.exc.InvalidTypeIdException.class)
                    .hasMessageContaining("denied");
        }
    }

    @Test
    void preservesImmutableCollectionsAndTypedTemporalRecordWithoutChangingWallTime() {
        var serializer = RedisConfig.cacheValueSerializer();
        var expected = new TemporalProbe(LocalDate.of(2026, 11, 1), LocalDateTime.of(2026, 11, 1, 1, 30),
                new BigDecimal("12.50"), ProbeStatus.READY, List.of(Map.of("count", 2L)));
        assertThat(serializer.deserialize(serializer.serialize(expected))).isEqualTo(expected);
    }

    record TemporalProbe(LocalDate date, LocalDateTime time, BigDecimal amount, ProbeStatus status,
                         List<Map<String, Object>> nested) { }
    enum ProbeStatus { READY }
}
