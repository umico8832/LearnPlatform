package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.FavoriteQuestionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.FavoriteService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FavoriteController MockMvc 集成测试
 */
@ExtendWith(MockitoExtension.class)
class FavoriteControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FavoriteService favoriteService;

    @InjectMocks
    private FavoriteController favoriteController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(favoriteController)
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

    // ======================== 收藏题目 ========================

    @Test
    void addFavorite_success() throws Exception {
        mockMvc.perform(post("/api/favorites/10").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(favoriteService).addFavorite(1L, 10L);
    }

    // ======================== 取消收藏 ========================

    @Test
    void removeFavorite_success() throws Exception {
        mockMvc.perform(delete("/api/favorites/10").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(favoriteService).removeFavorite(1L, 10L);
    }

    // ======================== 检查收藏状态 ========================

    @Test
    void checkFavorite_returnsStatus() throws Exception {
        when(favoriteService.isFavorite(eq(1L), eq(10L))).thenReturn(true);

        mockMvc.perform(get("/api/favorites/10/status").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.isFavorite").value(true));
    }

    @Test
    void checkFavorite_notFavorited() throws Exception {
        when(favoriteService.isFavorite(eq(1L), eq(20L))).thenReturn(false);

        mockMvc.perform(get("/api/favorites/20/status").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFavorite").value(false));
    }

    // ======================== 收藏列表 ========================

    @Test
    void getFavorites_returnsPage() throws Exception {
        Page<FavoriteQuestionVO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);

        when(favoriteService.getFavorites(eq(1L), eq(1), eq(10))).thenReturn(page);

        mockMvc.perform(get("/api/favorites").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @Test
    void getFavorites_customPageSize() throws Exception {
        Page<FavoriteQuestionVO> page = new Page<>(2, 5);
        FavoriteQuestionVO vo = new FavoriteQuestionVO();
        vo.setQuestionId(5L);
        page.setRecords(List.of(vo));
        page.setTotal(12);

        when(favoriteService.getFavorites(eq(1L), eq(2), eq(5))).thenReturn(page);

        mockMvc.perform(get("/api/favorites")
                        .with(mockUser(1L))
                        .param("pageNum", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(1));
    }

    // ======================== 收藏 ID 列表 ========================

    @Test
    void getFavoriteIds_returnsList() throws Exception {
        when(favoriteService.getFavoriteQuestionIds(eq(1L))).thenReturn(List.of(1L, 3L, 7L));

        mockMvc.perform(get("/api/favorites/ids").with(mockUser(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0]").value(1));
    }

    @Test
    void getFavoriteIds_emptyList() throws Exception {
        when(favoriteService.getFavoriteQuestionIds(eq(2L))).thenReturn(List.of());

        mockMvc.perform(get("/api/favorites/ids").with(mockUser(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}