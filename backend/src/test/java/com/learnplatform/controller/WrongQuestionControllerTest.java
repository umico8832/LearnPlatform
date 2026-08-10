package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.WrongQuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.WrongQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WrongQuestionController MockMvc 集成测试
 */
@ExtendWith(MockitoExtension.class)
class WrongQuestionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WrongQuestionService wrongQuestionService;

    @InjectMocks
    private WrongQuestionController wrongQuestionController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(wrongQuestionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    private RequestPostProcessor mockUser(Long userId) {
        return request -> {
            CustomUserDetails details = new CustomUserDetails(userId, "testuser", "USER");
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    details, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            return request;
        };
    }

    // ======================== 获取错题列表 ========================

    @Test
    void getWrongQuestions_defaultParams() throws Exception {
        Page<WrongQuestionVO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);

        when(wrongQuestionService.getWrongQuestions(
                eq(1L), eq(1), eq(10), isNull(), isNull(), isNull()))
                .thenReturn(page);

        mockMvc.perform(get("/api/wrong-questions").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @Test
    void getWrongQuestions_withFilters() throws Exception {
        Page<WrongQuestionVO> page = new Page<>(1, 5);
        page.setRecords(List.of());
        page.setTotal(0);

        when(wrongQuestionService.getWrongQuestions(
                eq(1L), eq(1), eq(5), eq(2L), eq(21L), eq(1)))
                .thenReturn(page);

        mockMvc.perform(get("/api/wrong-questions")
                        .with(mockUser(1L))
                        .param("pageSize", "5")
                        .param("courseId", "2")
                        .param("questionId", "21")
                        .param("masteryLevel", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(wrongQuestionService).getWrongQuestions(1L, 1, 5, 2L, 21L, 1);
    }

    // ======================== 错题统计 ========================

    @Test
    void getStats_returnsStats() throws Exception {
        when(wrongQuestionService.getWrongQuestionStats(1L))
                .thenReturn(Map.of("totalCount", 15, "unmasteredCount", 10));

        mockMvc.perform(get("/api/wrong-questions/stats").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(15));
    }

    // ======================== 更新掌握程度 ========================

    @Test
    void updateMasteryLevel_success() throws Exception {
        mockMvc.perform(put("/api/wrong-questions/5/mastery")
                        .with(mockUser(1L))
                        .param("masteryLevel", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(wrongQuestionService).updateMasteryLevel(5L, 1L, 2);
    }

    // ======================== 移出错题本 ========================

    @Test
    void removeWrongQuestion_success() throws Exception {
        mockMvc.perform(delete("/api/wrong-questions/3").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(wrongQuestionService).removeWrongQuestion(3L, 1L);
    }
}
