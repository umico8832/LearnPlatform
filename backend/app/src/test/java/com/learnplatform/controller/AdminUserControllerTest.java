package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.AdminUserPageVO;
import com.learnplatform.dto.AdminUserStatsVO;
import com.learnplatform.dto.AiQuotaAuditLogVO;
import com.learnplatform.dto.UserVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserDetails(99L, "admin", "ADMIN"),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        AdminUserController controller = new AdminUserController(adminUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listUsersDelegatesFiltersAndPreservesResponseShape() throws Exception {
        UserVO user = new UserVO();
        user.setId(1L);
        user.setUsername("testuser");
        when(adminUserService.listUsers(2, 20, "test", "ADMIN", 1))
                .thenReturn(new AdminUserPageVO(List.of(user), 1, 2, 20));

        mockMvc.perform(get("/api/admin/users")
                        .param("page", "2")
                        .param("size", "20")
                        .param("keyword", "test")
                        .param("role", "ADMIN")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].username").value("testuser"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void createUserValidatesRequestAndDelegates() throws Exception {
        UserVO user = new UserVO();
        user.setUsername("newuser");
        when(adminUserService.createUser(any())).thenReturn(user);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("newuser"));

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRoleDelegatesAndPropagatesBusinessFailure() throws Exception {
        mockMvc.perform(put("/api/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        verify(adminUserService).updateRole(1L, "ADMIN");

        doThrow(new BusinessException("用户不存在"))
                .when(adminUserService).updateRole(999L, "ADMIN");
        mockMvc.perform(put("/api/admin/users/999/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    void updateStatusRejectsInvalidValueBeforeService() throws Exception {
        mockMvc.perform(put("/api/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateQuotaPassesAuthenticatedAdminId() throws Exception {
        mockMvc.perform(put("/api/admin/users/1/ai-daily-quota")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyQuota\":120,\"reason\":\"学习计划调整\"}"))
                .andExpect(status().isOk());

        verify(adminUserService).updateAiDailyQuota(1L, 120, "学习计划调整", 99L);
    }

    @Test
    void updateQuotaRejectsInvalidRequest() throws Exception {
        mockMvc.perform(put("/api/admin/users/1/ai-daily-quota")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyQuota\":-1,\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listQuotaAuditsUsesVoPage() throws Exception {
        Page<AiQuotaAuditLogVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(new AiQuotaAuditLogVO(1L, 2L, 99L, null, 120,
                "学习计划调整", null)));
        when(adminUserService.listAiDailyQuotaAudits(2L, 1, 10)).thenReturn(page);

        mockMvc.perform(get("/api/admin/users/2/ai-daily-quota/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].adminUserId").value(99))
                .andExpect(jsonPath("$.data.records[0].reason").value("学习计划调整"));
    }

    @Test
    void resetPasswordValidatesAndDelegates() throws Exception {
        mockMvc.perform(put("/api/admin/users/1/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk());
        verify(adminUserService).resetPassword(1L, "newpass123");

        mockMvc.perform(put("/api/admin/users/1/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteUserDelegates() throws Exception {
        mockMvc.perform(delete("/api/admin/users/1"))
                .andExpect(status().isOk());
        verify(adminUserService).deleteUser(1L);
    }

    @Test
    void getUserStatsReturnsTypedResponse() throws Exception {
        when(adminUserService.getUserStats()).thenReturn(new AdminUserStatsVO(10, 8, 2, 1));

        mockMvc.perform(get("/api/admin/users/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.disabled").value(2));
    }
}
