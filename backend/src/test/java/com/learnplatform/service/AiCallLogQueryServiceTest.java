package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.AiCallLogStatsVO;
import com.learnplatform.dto.AiCallLogVO;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.mapper.AiCallLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCallLogQueryServiceTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;

    private AiCallLogQueryService service;

    @BeforeEach
    void setUp() {
        service = new AiCallLogQueryService(aiCallLogMapper);
    }

    @Test
    void listLogsMapsPersistenceEntityToVo() {
        AiCallLog entity = new AiCallLog();
        entity.setId(7L);
        entity.setFunctionType("EXPLANATION");
        entity.setPromptHash("hash");
        Page<AiCallLog> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(entity));
        when(aiCallLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        Page<AiCallLogVO> result = service.listLogs(1, 20, "EXPLANATION", 1);

        assertEquals(7L, result.getRecords().getFirst().id());
        assertEquals("EXPLANATION", result.getRecords().getFirst().functionType());
        assertEquals("hash", result.getRecords().getFirst().promptHash());
    }

    @Test
    void getStatsReturnsTypedCounts() {
        when(aiCallLogMapper.selectCount(null)).thenReturn(12L);
        when(aiCallLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L, 2L);

        AiCallLogStatsVO result = service.getStats();

        assertEquals(12, result.total());
        assertEquals(10, result.success());
        assertEquals(2, result.fail());
    }
}
