package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.AiCallLogStatsVO;
import com.learnplatform.dto.AiCallLogVO;
import com.learnplatform.service.AiCallLogQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAiCallLogControllerTest {

    @Mock
    private AiCallLogQueryService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAiCallLogController(service)).build();
    }

    @Test
    void listLogsDelegatesFiltersAndReturnsVoPage() throws Exception {
        AiCallLogVO log = new AiCallLogVO(
                1L, 2L, "EXPLANATION", "model", 10, 6, 4,
                null, 1, null, 100, "trace", "template", "hash", "config", null);
        Page<AiCallLogVO> page = new Page<>(2, 20, 1);
        page.setRecords(List.of(log));
        when(service.listLogs(2, 20, "EXPLANATION", 1)).thenReturn(page);

        mockMvc.perform(get("/api/admin/ai-logs")
                        .param("page", "2")
                        .param("size", "20")
                        .param("functionType", "EXPLANATION")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].functionType").value("EXPLANATION"))
                .andExpect(jsonPath("$.data.records[0].promptHash").value("hash"));

        verify(service).listLogs(2, 20, "EXPLANATION", 1);
    }

    @Test
    void getStatsReturnsTypedResponse() throws Exception {
        when(service.getStats()).thenReturn(new AiCallLogStatsVO(12, 10, 2));

        mockMvc.perform(get("/api/admin/ai-logs/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.success").value(10))
                .andExpect(jsonPath("$.data.fail").value(2));
    }
}
