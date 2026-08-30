package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.AdminCreateUserRequest;
import com.learnplatform.dto.AdminUserPageVO;
import com.learnplatform.dto.AdminUserStatsVO;
import com.learnplatform.dto.AiQuotaAuditLogVO;
import com.learnplatform.dto.UserVO;
import com.learnplatform.entity.AiQuotaAuditLog;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.AiQuotaAuditLogMapper;
import com.learnplatform.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AiQuotaAuditLogMapper aiQuotaAuditLogMapper;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userMapper, passwordEncoder, aiQuotaAuditLogMapper);
    }

    @Test
    void listUsersMapsEntitiesToVo() {
        Page<User> page = new Page<>(2, 20, 1);
        page.setRecords(List.of(user(1L, "testuser")));
        when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        AdminUserPageVO result = service.listUsers(2, 20, "test", "ADMIN", 1);

        assertEquals("testuser", result.records().getFirst().getUsername());
        assertEquals(1, result.total());
        assertEquals(2, result.current());
    }

    @Test
    void createUserEncodesPasswordAndAppliesDefaults() {
        AdminCreateUserRequest request = createRequest("newuser", "password123", null, null);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");

        UserVO result = service.createUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("encoded", captor.getValue().getPassword());
        assertEquals("newuser", captor.getValue().getNickname());
        assertEquals("USER", captor.getValue().getRole());
        assertEquals("newuser", result.getUsername());
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        AdminCreateUserRequest request = createRequest("existing", "password123", null, null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.createUser(request));

        assertEquals("用户名已存在", error.getMessage());
        verify(userMapper, never()).insert(any());
    }

    @Test
    void updateRoleValidatesBusinessRole() {
        User user = user(1L, "testuser");
        when(userMapper.selectById(1L)).thenReturn(user);

        service.updateRole(1L, "ADMIN");
        assertEquals("ADMIN", user.getRole());
        verify(userMapper).updateById(user);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateRole(1L, "SUPERADMIN"));
        assertEquals("角色只能为 USER 或 ADMIN", error.getMessage());
    }

    @Test
    void updateStatusValidatesBusinessStatus() {
        User user = user(1L, "testuser");
        when(userMapper.selectById(1L)).thenReturn(user);

        service.updateStatus(1L, 0);
        assertEquals(0, user.getStatus());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateStatus(1L, 2));
        assertEquals("状态只能为 0（禁用）或 1（启用）", error.getMessage());
    }

    @Test
    void mutationsRejectUnknownUser() {
        when(userMapper.selectById(999L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.resetPassword(999L, "newpass123"));

        assertEquals("用户不存在", error.getMessage());
    }

    @Test
    void updateAiDailyQuotaWritesUserAndAuditInOneServiceOperation() {
        User user = user(1L, "testuser");
        user.setAiDailyQuota(30);
        when(userMapper.selectById(1L)).thenReturn(user);

        service.updateAiDailyQuota(1L, 120, "  学习计划调整  ", 99L);

        assertEquals(120, user.getAiDailyQuota());
        ArgumentCaptor<AiQuotaAuditLog> captor = ArgumentCaptor.forClass(AiQuotaAuditLog.class);
        verify(aiQuotaAuditLogMapper).insert(captor.capture());
        assertEquals(30, captor.getValue().getPreviousDailyQuota());
        assertEquals(120, captor.getValue().getNewDailyQuota());
        assertEquals(99L, captor.getValue().getAdminUserId());
        assertEquals("学习计划调整", captor.getValue().getReason());
    }

    @Test
    void nullQuotaRestoresGlobalDefault() {
        User user = user(1L, "testuser");
        user.setAiDailyQuota(120);
        when(userMapper.selectById(1L)).thenReturn(user);

        service.updateAiDailyQuota(1L, null, "恢复默认策略", 99L);

        assertNull(user.getAiDailyQuota());
    }

    @Test
    void listAiDailyQuotaAuditsMapsEntitiesToVo() {
        AiQuotaAuditLog entity = new AiQuotaAuditLog();
        entity.setId(4L);
        entity.setUserId(1L);
        entity.setReason("调整");
        Page<AiQuotaAuditLog> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(entity));
        when(aiQuotaAuditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        Page<AiQuotaAuditLogVO> result = service.listAiDailyQuotaAudits(1L, 1, 10);

        assertEquals(4L, result.getRecords().getFirst().id());
        assertEquals("调整", result.getRecords().getFirst().reason());
    }

    @Test
    void resetPasswordEncodesAndPersists() {
        User user = user(1L, "testuser");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.encode("newpass123")).thenReturn("encoded-new");

        service.resetPassword(1L, "newpass123");

        assertEquals("encoded-new", user.getPassword());
        verify(userMapper).updateById(user);
    }

    @Test
    void deleteUserChecksExistenceBeforeLogicalDelete() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "testuser"));

        service.deleteUser(1L);

        verify(userMapper).deleteById(1L);
    }

    @Test
    void getUserStatsBuildsTypedSummary() {
        when(userMapper.selectCount(null)).thenReturn(10L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(8L, 1L);

        AdminUserStatsVO result = service.getUserStats();

        assertEquals(10, result.total());
        assertEquals(8, result.active());
        assertEquals(2, result.disabled());
        assertEquals(1, result.admins());
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username + "_nick");
        user.setRole("USER");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        return user;
    }

    private AdminCreateUserRequest createRequest(String username, String password, String nickname, String role) {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setNickname(nickname);
        request.setRole(role);
        return request;
    }
}
