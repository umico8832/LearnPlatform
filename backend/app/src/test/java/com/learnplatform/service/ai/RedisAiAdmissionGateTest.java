package com.learnplatform.service.ai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisAiAdmissionGateTest {
    @Test void acquiredLeaseRenewsAndReleasesByItsOwnToken() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), eq(List.of(RedisAiAdmissionGate.KEY)), any(Object[].class)))
                .thenReturn(1L);
        var gate = new RedisAiAdmissionGate(redis, 8, Duration.ofSeconds(30));

        var lease = gate.tryAcquire();
        assertNotNull(lease);
        assertTrue(lease.renew());
        lease.close();
        lease.close();

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(redis, times(3)).execute(any(RedisScript.class), eq(List.of(RedisAiAdmissionGate.KEY)),
                arguments.capture());
        String token = (String) arguments.getAllValues().getFirst()[2];
        assertEquals(token, arguments.getAllValues().get(1)[0]);
        assertArrayEquals(new Object[]{token}, arguments.getAllValues().get(2));
    }

    @Test void fullOrExpiredLeaseIsNotAdmittedOrRenewed() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), eq(List.of(RedisAiAdmissionGate.KEY)), any(Object[].class)))
                .thenReturn(0L);
        var gate = new RedisAiAdmissionGate(redis, 8, Duration.ofSeconds(30));
        assertNull(gate.tryAcquire());
    }
}
