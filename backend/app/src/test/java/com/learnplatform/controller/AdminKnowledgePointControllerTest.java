package com.learnplatform.controller;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.KnowledgePointVO;
import com.learnplatform.service.KnowledgePointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminKnowledgePointController MockMvc 集成测试
 */
@ExtendWith(MockitoExtension.class)
class AdminKnowledgePointControllerTest {

    private MockMvc mockMvc;

    @Mock
    private KnowledgePointService knowledgePointService;

    @InjectMocks
    private AdminKnowledgePointController adminKPController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminKPController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private KnowledgePointVO buildKPVO(Long id, String name) {
        KnowledgePointVO vo = new KnowledgePointVO();
        vo.setId(id);
        vo.setCourseId(1L);
        vo.setParentId(0L);
        vo.setName(name);
        vo.setDescription(name + "描述");
        vo.setSortOrder(0);
        return vo;
    }

    @Test
    void createKnowledgePoint_success() throws Exception {
        KnowledgePointVO vo = buildKPVO(1L, "变量与数据类型");
        when(knowledgePointService.createKnowledgePoint(eq(1L), eq(0L), eq("变量与数据类型"), eq("变量描述"), eq(0)))
                .thenReturn(vo);

        mockMvc.perform(post("/api/admin/knowledge-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"parentId\":0,\"name\":\"变量与数据类型\",\"description\":\"变量描述\",\"sortOrder\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("变量与数据类型"))
                .andExpect(jsonPath("$.data.courseId").value(1));

        verify(knowledgePointService).createKnowledgePoint(eq(1L), eq(0L), eq("变量与数据类型"), eq("变量描述"), eq(0));
    }

    @Test
    void createKnowledgePoint_minimalFields() throws Exception {
        KnowledgePointVO vo = buildKPVO(1L, "简单知识点");
        when(knowledgePointService.createKnowledgePoint(eq(1L), any(), eq("简单知识点"), any(), any()))
                .thenReturn(vo);

        mockMvc.perform(post("/api/admin/knowledge-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"name\":\"简单知识点\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("简单知识点"));
    }

    @Test
    void createKnowledgePoint_serviceError() throws Exception {
        when(knowledgePointService.createKnowledgePoint(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException("课程不存在"));

        mockMvc.perform(post("/api/admin/knowledge-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":999,\"name\":\"测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("课程不存在"));
    }

    @Test
    void updateKnowledgePoint_success() throws Exception {
        KnowledgePointVO vo = buildKPVO(1L, "更新后的知识点");
        when(knowledgePointService.updateKnowledgePoint(eq(1L), eq("更新后的知识点"), eq("新描述"), eq(1)))
                .thenReturn(vo);

        mockMvc.perform(put("/api/admin/knowledge-points/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"更新后的知识点\",\"description\":\"新描述\",\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("更新后的知识点"));

        verify(knowledgePointService).updateKnowledgePoint(eq(1L), eq("更新后的知识点"), eq("新描述"), eq(1));
    }

    @Test
    void updateKnowledgePoint_notFound() throws Exception {
        when(knowledgePointService.updateKnowledgePoint(eq(999L), any(), any(), any()))
                .thenThrow(new BusinessException("知识点不存在"));

        mockMvc.perform(put("/api/admin/knowledge-points/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不存在\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("知识点不存在"));
    }

    @Test
    void deleteKnowledgePoint_success() throws Exception {
        doNothing().when(knowledgePointService).deleteKnowledgePoint(eq(1L));

        mockMvc.perform(delete("/api/admin/knowledge-points/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(knowledgePointService).deleteKnowledgePoint(eq(1L));
    }

    @Test
    void deleteKnowledgePoint_notFound() throws Exception {
        doThrow(new BusinessException("知识点不存在")).when(knowledgePointService).deleteKnowledgePoint(eq(999L));

        mockMvc.perform(delete("/api/admin/knowledge-points/{id}", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("知识点不存在"));
    }
}