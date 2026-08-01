package com.learnplatform.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.PasswordResetToken;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.PasswordResetTokenMapper;
import com.learnplatform.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private final PasswordResetTokenMapper resetMapper;
    private final UserMapper userMapper;
    private final AuthTokenHasher tokenHasher;
    private final AuthMailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final String frontendUrl;

    public PasswordResetService(PasswordResetTokenMapper resetMapper,
                                UserMapper userMapper,
                                AuthTokenHasher tokenHasher,
                                AuthMailService mailService,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.resetMapper = resetMapper;
        this.userMapper = userMapper;
        this.tokenHasher = tokenHasher;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void requestReset(String rawEmail, String ipAddress) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        if (user == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long count = resetMapper.selectCount(Wrappers.<PasswordResetToken>lambdaQuery()
                .eq(PasswordResetToken::getUserId, user.getId())
                .gt(PasswordResetToken::getCreateTime, now.minusHours(1)));
        if (count >= 3) {
            return;
        }
        String token = tokenHasher.randomToken();
        PasswordResetToken reset = new PasswordResetToken();
        reset.setUserId(user.getId());
        reset.setTokenHash(tokenHasher.hash(token));
        reset.setExpiresAt(now.plusMinutes(30));
        reset.setIpAddress(ipAddress);
        reset.setCreateTime(now);
        resetMapper.insert(reset);
        try {
            mailService.sendPasswordResetLink(email, frontendUrl + "/reset-password?token=" + token);
        } catch (MailException e) {
            log.error("密码重置邮件发送失败: userId={}", user.getId());
        }
    }

    public String validateToken(String token) {
        PasswordResetToken reset = findValid(token);
        User user = userMapper.selectById(reset.getUserId());
        if (user == null || user.getEmail() == null) {
            throw invalidToken();
        }
        return user.getEmail();
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken reset = findValid(token);
        LocalDateTime now = LocalDateTime.now();
        int consumed = resetMapper.update(null, Wrappers.<PasswordResetToken>lambdaUpdate()
                .set(PasswordResetToken::getUsedAt, now)
                .eq(PasswordResetToken::getId, reset.getId())
                .isNull(PasswordResetToken::getUsedAt)
                .gt(PasswordResetToken::getExpiresAt, now));
        if (consumed != 1) {
            throw invalidToken();
        }
        User user = userMapper.selectById(reset.getUserId());
        if (user == null) {
            throw invalidToken();
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setAuthVersion((user.getAuthVersion() == null ? 0 : user.getAuthVersion()) + 1);
        user.setUpdateTime(now);
        userMapper.updateById(user);
    }

    private PasswordResetToken findValid(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }
        PasswordResetToken reset = resetMapper.selectOne(Wrappers.<PasswordResetToken>lambdaQuery()
                .eq(PasswordResetToken::getTokenHash, tokenHasher.hash(token))
                .isNull(PasswordResetToken::getUsedAt)
                .gt(PasswordResetToken::getExpiresAt, LocalDateTime.now()));
        if (reset == null) {
            throw invalidToken();
        }
        return reset;
    }

    private BusinessException invalidToken() {
        return new BusinessException(ResultCode.VALIDATION_ERROR, "重置链接无效或已过期");
    }
}
