package com.learnplatform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.UserVO;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminUserController MockMvc 集成测试
 */
@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserController adminUserController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private User buildUser(Long id, String username, String role, Integer status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username + "_nick");
        user.setRole(role);
        user.setStatus(status);
        user.setCreateTime(LocalDateTime.now());
        return user;
    }

    @Test
    void listUsers_defaultParams() throws Exception {
        Page<User> page = new Page<>(1, 10);
        User user = buildUser(1L, "testuser", "USER", 1);
        page.setRecords(List.of(user));
        page.setTotal(1);
        when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].username").value("testuser"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void listUsers_withFilters() throws Exception {
        Page<User> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/users")
                        .param("keyword", "test")
                        .param("role", "ADMIN")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void createUser_success() throws Exception {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_pwd");
        when(userMapper.insert(any(User.class))).thenReturn(1);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"password123\",\"nickname\":\"New User\",\"role\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("newuser"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertEquals("encoded_pwd", saved.getPassword());
        assertEquals("USER", saved.getRole());
        assertEquals(1, saved.getStatus());
    }

    @Test
    void createUser_defaultNicknameAndRole() throws Exception {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_pwd");
        when(userMapper.insert(any(User.class))).thenReturn(1);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertEquals("newuser", saved.getNickname());
        assertEquals("USER", saved.getRole());
    }

    @Test
    void createUser_duplicateUsername() throws Exception {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"existing\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("用户名已存在"));

        verify(userMapper, never()).insert(any());
    }

    @Test
    void createUser_validation_blankUsername() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_validation_shortPassword() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRole_success() throws Exception {
        User user = buildUser(1L, "testuser", "USER", 1);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        mockMvc.perform(put("/api/admin/users/{id}/role", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertEquals("ADMIN", user.getRole());
        verify(userMapper).updateById(user);
    }

    @Test
    void updateRole_invalidRole() throws Exception {
        User user = buildUser(1L, "testuser", "USER", 1);
        when(userMapper.selectById(1L)).thenReturn(user);

        mockMvc.perform(put("/api/admin/users/{id}/role", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPERADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("角色只能为 USER 或 ADMIN"));
    }

    @Test
    void updateRole_userNotFound() throws Exception {
        when(userMapper.selectById(999L)).thenReturn(null);

        mockMvc.perform(put("/api/admin/users/{id}/role", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    void updateRole_validation_blankRole() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}/role", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_enable() throws Exception {
        User user = buildUser(1L, "testuser", "USER", 0);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        mockMvc.perform(put("/api/admin/users/{id}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertEquals(1, user.getStatus());
    }

    @Test
    void updateStatus_disable() throws Exception {
        User user = buildUser(1L, "testuser", "USER", 1);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        mockMvc.perform(put("/api/admin/users/{id}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertEquals(0, user.getStatus());
    }

    @Test
    void updateStatus_invalidStatus() throws Exception {
        // status=2 triggers @Max(1) validation → 400 before controller logic
        mockMvc.perform(put("/api/admin/users/{id}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_userNotFound() throws Exception {
        when(userMapper.selectById(999L)).thenReturn(null);

        mockMvc.perform(put("/api/admin/users/{id}/status", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    void updateAiDailyQuota_setsCustomQuota() throws Exception {
        User user = buildUser(1L, "testuser", "USER", 1);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        mockMvc.perform(put("/api/admin/users/{id}/ai-daily-quota", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyQuota\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertEquals(120, user.getAiDailyQuota());
    }

    @Test
    void updateAiDailyQuota_nullRestoresGlobalDefault() throws Exception {
        User user = buildUser(1L, "testuser", "USER", 1);
        user.setAiDailyQuota(120);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        mockMvc.perform(put("/api/admin/users/{id}/ai-daily-quota", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyQuota\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertNull(user.getAiDailyQuota());
    }

    @Test
    void updateAiDailyQuota_rejectsNegativeQuota() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}/ai-daily-quota", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyQuota\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_success() throws Exception {
        User user = buildUser(1L, "testuser", "USER", 1);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.encode("newpass123")).thenReturn("encoded_new");
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        mockMvc.perform(put("/api/admin/users/{id}/reset-password", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertEquals("encoded_new", user.getPassword());
        verify(userMapper).updateById(user);
    }

    @Test
    void resetPassword_userNotFound() throws Exception {
        when(userMapper.selectById(999L)).thenReturn(null);

        mockMvc.perform(put("/api/admin/users/{id}/reset-password", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    void resetPassword_validation_blankPassword() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}/reset-password", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteUser_success() throws Exception {
        User user = buildUser(1L, "testuser", "USER", 1);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.deleteById(1L)).thenReturn(1);

        mockMvc.perform(delete("/api/admin/users/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userMapper).deleteById(1L);
    }

    @Test
    void deleteUser_notFound() throws Exception {
        when(userMapper.selectById(999L)).thenReturn(null);

        mockMvc.perform(delete("/api/admin/users/{id}", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    void getUserStats_success() throws Exception {
        when(userMapper.selectCount(isNull())).thenReturn(10L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(8L)   // active users
                .thenReturn(2L);  // admin users

        mockMvc.perform(get("/api/admin/users/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.active").value(8))
                .andExpect(jsonPath("$.data.disabled").value(2))
                .andExpect(jsonPath("$.data.admins").value(2));
    }
}
