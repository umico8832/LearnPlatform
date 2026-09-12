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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AiQuotaAuditLogMapper aiQuotaAuditLogMapper;

    public AdminUserService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                            AiQuotaAuditLogMapper aiQuotaAuditLogMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.aiQuotaAuditLogMapper = aiQuotaAuditLogMapper;
    }

    public AdminUserPageVO listUsers(int page, int size, String keyword, String role, Integer status) {
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

        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserVO> records = result.getRecords().stream().map(UserVO::fromUser).toList();
        return new AdminUserPageVO(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public UserVO createUser(AdminCreateUserRequest request) {
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
        return UserVO.fromUser(user);
    }

    public void updateRole(Long id, String role) {
        User user = requireUser(id);
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new BusinessException("角色只能为 USER 或 ADMIN");
        }
        user.setRole(role);
        userMapper.updateById(user);
        log.info("管理员修改用户角色: userId={}, role={}", id, role);
    }

    public void updateStatus(Long id, Integer status) {
        User user = requireUser(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态只能为 0（禁用）或 1（启用）");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        log.info("管理员修改用户状态: userId={}, status={}", id, status);
    }

    @Transactional
    public void updateAiDailyQuota(Long id, Integer dailyQuota, String reason, Long adminUserId) {
        User user = requireUser(id);
        Integer previousDailyQuota = user.getAiDailyQuota();
        user.setAiDailyQuota(dailyQuota);
        userMapper.updateById(user);

        AiQuotaAuditLog auditLog = new AiQuotaAuditLog();
        auditLog.setUserId(id);
        auditLog.setAdminUserId(adminUserId);
        auditLog.setPreviousDailyQuota(previousDailyQuota);
        auditLog.setNewDailyQuota(dailyQuota);
        auditLog.setReason(reason.trim());
        aiQuotaAuditLogMapper.insert(auditLog);
        log.info("管理员设置用户 AI 日配额: userId={}, adminUserId={}, previousDailyQuota={}, dailyQuota={}",
                id, adminUserId, previousDailyQuota, dailyQuota);
    }

    public Page<AiQuotaAuditLogVO> listAiDailyQuotaAudits(Long id, int page, int size) {
        Page<AiQuotaAuditLog> result = aiQuotaAuditLogMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<AiQuotaAuditLog>()
                        .eq(AiQuotaAuditLog::getUserId, id)
                        .orderByDesc(AiQuotaAuditLog::getCreateTime));
        Page<AiQuotaAuditLogVO> response = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        response.setRecords(result.getRecords().stream().map(AiQuotaAuditLogVO::fromEntity).toList());
        return response;
    }

    public void resetPassword(Long id, String newPassword) {
        User user = requireUser(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        log.info("管理员重置用户密码: userId={}", id);
    }

    public void deleteUser(Long id) {
        requireUser(id);
        userMapper.deleteById(id);
        log.info("管理员删除用户: userId={}", id);
    }

    public AdminUserStatsVO getUserStats() {
        long totalUsers = userMapper.selectCount(null);
        long activeUsers = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        long adminUsers = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN"));
        return new AdminUserStatsVO(totalUsers, activeUsers, totalUsers - activeUsers, adminUsers);
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }
}
