package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.AdminCreateUserRequest;
import com.learnplatform.dto.AdminUserPageVO;
import com.learnplatform.dto.AdminUserStatsVO;
import com.learnplatform.dto.AiQuotaAuditLogVO;
import com.learnplatform.dto.ResetUserPasswordRequest;
import com.learnplatform.dto.UpdateAiDailyQuotaRequest;
import com.learnplatform.dto.UpdateUserRoleRequest;
import com.learnplatform.dto.UpdateUserStatusRequest;
import com.learnplatform.dto.UserVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户管理控制器
 */
@Tag(name = "管理端用户管理", description = "管理员对用户进行列表查看、新增、修改角色、启停和重置密码")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 用户分页列表
     */
    @Operation(summary = "用户分页列表")
    @GetMapping
    public R<AdminUserPageVO> listUsers(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "用户名/昵称关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "角色筛选") @RequestParam(required = false) String role,
            @Parameter(description = "状态筛选 0-禁用 1-启用") @RequestParam(required = false) Integer status
    ) {
        return R.ok(adminUserService.listUsers(page, size, keyword, role, status));
    }

    /**
     * 管理员创建用户
     */
    @Operation(summary = "管理员创建用户")
    @PostMapping
    public R<UserVO> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return R.ok(adminUserService.createUser(request));
    }

    /**
     * 修改用户角色
     */
    @Operation(summary = "修改用户角色")
    @PutMapping("/{id}/role")
    public R<Void> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        adminUserService.updateRole(id, request.getRole());
        return R.ok();
    }

    /**
     * 启用/禁用用户
     */
    @Operation(summary = "启用/禁用用户")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        adminUserService.updateStatus(id, request.getStatus());
        return R.ok();
    }

    /**
     * 设置用户级 AI 日配额。null 表示恢复继承全局配置，0 表示不限次数。
     */
    @Operation(summary = "设置用户 AI 日配额", description = "dailyQuota 为 null 时继承全局配置，0 表示不限次数")
    @PutMapping("/{id}/ai-daily-quota")
    public R<Void> updateAiDailyQuota(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAiDailyQuotaRequest request,
            @AuthenticationPrincipal CustomUserDetails adminUser
    ) {
        adminUserService.updateAiDailyQuota(
                id, request.getDailyQuota(), request.getReason(), adminUser.getUserId());
        return R.ok();
    }

    @Operation(summary = "查询用户 AI 日配额调整记录")
    @GetMapping("/{id}/ai-daily-quota/audits")
    public R<Page<AiQuotaAuditLogVO>> listAiDailyQuotaAudits(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(adminUserService.listAiDailyQuotaAudits(id, page, size));
    }

    /**
     * 重置用户密码
     */
    @Operation(summary = "重置用户密码")
    @PutMapping("/{id}/reset-password")
    public R<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetUserPasswordRequest request
    ) {
        adminUserService.resetPassword(id, request.getNewPassword());
        return R.ok();
    }

    /**
     * 删除用户（逻辑删除）
     */
    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public R<Void> deleteUser(@PathVariable Long id) {
        adminUserService.deleteUser(id);
        return R.ok();
    }

    /**
     * 获取用户统计概览
     */
    @Operation(summary = "用户统计概览")
    @GetMapping("/stats")
    public R<AdminUserStatsVO> getUserStats() {
        return R.ok(adminUserService.getUserStats());
    }
}
