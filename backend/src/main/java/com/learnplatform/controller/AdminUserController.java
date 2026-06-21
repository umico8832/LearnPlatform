package com.learnplatform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.UserVO;
import com.learnplatform.entity.User;
import com.learnplatform.entity.AiQuotaAuditLog;
import com.learnplatform.mapper.AiQuotaAuditLogMapper;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端用户管理控制器
 */
@Tag(name = "管理端用户管理", description = "管理员对用户进行列表查看、新增、修改角色、启停和重置密码")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AiQuotaAuditLogMapper aiQuotaAuditLogMapper;

    public AdminUserController(UserMapper userMapper, PasswordEncoder passwordEncoder,
                               AiQuotaAuditLogMapper aiQuotaAuditLogMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.aiQuotaAuditLogMapper = aiQuotaAuditLogMapper;
    }

    /**
     * 用户分页列表
     */
    @Operation(summary = "用户分页列表")
    @GetMapping
    public R<Map<String, Object>> listUsers(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "用户名/昵称关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "角色筛选") @RequestParam(required = false) String role,
            @Parameter(description = "状态筛选 0-禁用 1-启用") @RequestParam(required = false) Integer status
    ) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getNickname, keyword));
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(User::getRole, role);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreateTime);

        Page<User> pageObj = userMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageObj.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        result.put("total", pageObj.getTotal());
        result.put("current", pageObj.getCurrent());
        result.put("size", pageObj.getSize());
        return R.ok(result);
    }

    /**
     * 管理员创建用户
     */
    @Operation(summary = "管理员创建用户")
    @PostMapping
    public R<UserVO> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        // 检查用户名唯一性
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setRole(request.getRole() != null ? request.getRole() : "USER");
        user.setStatus(1);
        userMapper.insert(user);

        log.info("管理员创建用户: username={}, role={}", user.getUsername(), user.getRole());
        return R.ok(toVO(user));
    }

    /**
     * 修改用户角色
     */
    @Operation(summary = "修改用户角色")
    @PutMapping("/{id}/role")
    public R<Void> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!"USER".equals(request.getRole()) && !"ADMIN".equals(request.getRole())) {
            throw new BusinessException("角色只能为 USER 或 ADMIN");
        }
        user.setRole(request.getRole());
        userMapper.updateById(user);
        log.info("管理员修改用户角色: userId={}, role={}", id, request.getRole());
        return R.ok();
    }

    /**
     * 启用/禁用用户
     */
    @Operation(summary = "启用/禁用用户")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (request.getStatus() != 0 && request.getStatus() != 1) {
            throw new BusinessException("状态只能为 0（禁用）或 1（启用）");
        }
        user.setStatus(request.getStatus());
        userMapper.updateById(user);
        log.info("管理员修改用户状态: userId={}, status={}", id, request.getStatus());
        return R.ok();
    }

    /**
     * 设置用户级 AI 日配额。null 表示恢复继承全局配置，0 表示不限次数。
     */
    @Operation(summary = "设置用户 AI 日配额", description = "dailyQuota 为 null 时继承全局配置，0 表示不限次数")
    @PutMapping("/{id}/ai-daily-quota")
    @Transactional
    public R<Void> updateAiDailyQuota(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAiDailyQuotaRequest request,
            @AuthenticationPrincipal CustomUserDetails adminUser
    ) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Integer previousDailyQuota = user.getAiDailyQuota();
        user.setAiDailyQuota(request.getDailyQuota());
        userMapper.updateById(user);
        AiQuotaAuditLog auditLog = new AiQuotaAuditLog();
        auditLog.setUserId(id);
        auditLog.setAdminUserId(adminUser.getUserId());
        auditLog.setPreviousDailyQuota(previousDailyQuota);
        auditLog.setNewDailyQuota(request.getDailyQuota());
        auditLog.setReason(request.getReason().trim());
        aiQuotaAuditLogMapper.insert(auditLog);
        log.info("管理员设置用户 AI 日配额: userId={}, adminUserId={}, previousDailyQuota={}, dailyQuota={}",
                id, adminUser.getUserId(), previousDailyQuota, request.getDailyQuota());
        return R.ok();
    }

    @Operation(summary = "查询用户 AI 日配额调整记录")
    @GetMapping("/{id}/ai-daily-quota/audits")
    public R<Page<AiQuotaAuditLog>> listAiDailyQuotaAudits(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<AiQuotaAuditLog> result = aiQuotaAuditLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AiQuotaAuditLog>()
                        .eq(AiQuotaAuditLog::getUserId, id)
                        .orderByDesc(AiQuotaAuditLog::getCreateTime));
        return R.ok(result);
    }

    /**
     * 重置用户密码
     */
    @Operation(summary = "重置用户密码")
    @PutMapping("/{id}/reset-password")
    public R<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        log.info("管理员重置用户密码: userId={}", id);
        return R.ok();
    }

    /**
     * 删除用户（逻辑删除）
     */
    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public R<Void> deleteUser(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        userMapper.deleteById(id);
        log.info("管理员删除用户: userId={}", id);
        return R.ok();
    }

    /**
     * 获取用户统计概览
     */
    @Operation(summary = "用户统计概览")
    @GetMapping("/stats")
    public R<Map<String, Object>> getUserStats() {
        long totalUsers = userMapper.selectCount(null);
        long activeUsers = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        long adminUsers = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN"));

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", totalUsers);
        stats.put("active", activeUsers);
        stats.put("disabled", totalUsers - activeUsers);
        stats.put("admins", adminUsers);
        return R.ok(stats);
    }

    private UserVO toVO(User user) {
        return UserVO.fromUser(user);
    }

    // ========== 请求体 ==========

    public static class AdminCreateUserRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度3-50个字符")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度6-100个字符")
        private String password;

        private String nickname;
        private String role;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    public static class UpdateRoleRequest {
        @NotBlank(message = "角色不能为空")
        private String role;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    public static class UpdateStatusRequest {
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态只能为0或1")
        @Max(value = 1, message = "状态只能为0或1")
        private Integer status;

        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    public static class ResetPasswordRequest {
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度6-100个字符")
        private String newPassword;

        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    public static class UpdateAiDailyQuotaRequest {
        @Min(value = 0, message = "AI 日配额不能小于 0")
        @Max(value = 10000, message = "AI 日配额不能超过 10000")
        private Integer dailyQuota;
        @NotBlank(message = "调整原因不能为空")
        @Size(max = 500, message = "调整原因不能超过 500 个字符")
        private String reason;

        public Integer getDailyQuota() { return dailyQuota; }
        public void setDailyQuota(Integer dailyQuota) { this.dailyQuota = dailyQuota; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
