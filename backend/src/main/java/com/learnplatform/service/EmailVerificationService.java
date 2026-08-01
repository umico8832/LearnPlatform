package com.learnplatform.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.VerificationTicketResponse;
import com.learnplatform.entity.EmailVerification;
import com.learnplatform.mapper.EmailVerificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class EmailVerificationService {
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SENDS_PER_HOUR = 5;
    private static final String PURPOSE_REGISTER = "register";

    private final EmailVerificationMapper mapper;
    private final AuthTokenHasher tokenHasher;
    private final AuthMailService mailService;

    public EmailVerificationService(EmailVerificationMapper mapper,
                                    AuthTokenHasher tokenHasher,
                                    AuthMailService mailService) {
        this.mapper = mapper;
        this.tokenHasher = tokenHasher;
        this.mailService = mailService;
    }

    @Transactional
    public void sendRegistrationCode(String rawEmail, String ipAddress, String userAgent) {
        String email = normalizeEmail(rawEmail);
        LocalDateTime now = LocalDateTime.now();
        Long recentCount = mapper.selectCount(Wrappers.<EmailVerification>lambdaQuery()
                .eq(EmailVerification::getEmail, email)
                .eq(EmailVerification::getPurpose, PURPOSE_REGISTER)
                .gt(EmailVerification::getCreateTime, now.minusHours(1)));
        if (recentCount >= MAX_SENDS_PER_HOUR) {
            throw new BusinessException(ResultCode.RATE_LIMITED, "验证码发送过于频繁，请稍后再试");
        }
        Long cooldownCount = mapper.selectCount(Wrappers.<EmailVerification>lambdaQuery()
                .eq(EmailVerification::getEmail, email)
                .eq(EmailVerification::getPurpose, PURPOSE_REGISTER)
                .gt(EmailVerification::getCreateTime, now.minusMinutes(1)));
        Long ipCount = mapper.selectCount(Wrappers.<EmailVerification>lambdaQuery()
                .eq(EmailVerification::getIpAddress, ipAddress)
                .gt(EmailVerification::getCreateTime, now.minusHours(1)));
        if (cooldownCount > 0 || ipCount >= 20) {
            throw new BusinessException(ResultCode.RATE_LIMITED, "验证码发送过于频繁，请稍后再试");
        }

        String code = tokenHasher.randomCode();
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setPurpose(PURPOSE_REGISTER);
        verification.setCodeHash(hashCode(email, code));
        verification.setAttemptCount(0);
        verification.setExpiresAt(now.plusMinutes(10));
        verification.setIpAddress(ipAddress);
        verification.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(500, userAgent.length())));
        verification.setCreateTime(now);
        mapper.insert(verification);
        mailService.sendVerificationCode(email, code);
    }

    @Transactional
    public VerificationTicketResponse verifyRegistrationCode(String rawEmail, String code) {
        String email = normalizeEmail(rawEmail);
        LocalDateTime now = LocalDateTime.now();
        EmailVerification verification = mapper.selectOne(Wrappers.<EmailVerification>lambdaQuery()
                .eq(EmailVerification::getEmail, email)
                .eq(EmailVerification::getPurpose, PURPOSE_REGISTER)
                .isNull(EmailVerification::getVerifiedAt)
                .isNull(EmailVerification::getUsedAt)
                .gt(EmailVerification::getExpiresAt, now)
                .orderByDesc(EmailVerification::getCreateTime)
                .last("LIMIT 1"));
        if (verification == null || verification.getAttemptCount() >= MAX_ATTEMPTS) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "验证码无效或已过期");
        }

        verification.setAttemptCount(verification.getAttemptCount() + 1);
        if (!MessageDigest.isEqual(
                verification.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hashCode(email, code).getBytes(StandardCharsets.UTF_8))) {
            mapper.updateById(verification);
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "验证码无效或已过期");
        }

        String ticket = tokenHasher.randomToken();
        verification.setTicketHash(tokenHasher.hash(ticket));
        verification.setVerifiedAt(now);
        verification.setExpiresAt(now.plusMinutes(5));
        mapper.updateById(verification);
        return new VerificationTicketResponse(ticket, 300);
    }

    public void consumeRegistrationTicket(String rawEmail, String ticket) {
        String email = normalizeEmail(rawEmail);
        LocalDateTime now = LocalDateTime.now();
        int updated = mapper.update(null, Wrappers.<EmailVerification>lambdaUpdate()
                .set(EmailVerification::getUsedAt, now)
                .eq(EmailVerification::getEmail, email)
                .eq(EmailVerification::getPurpose, PURPOSE_REGISTER)
                .eq(EmailVerification::getTicketHash, tokenHasher.hash(ticket))
                .isNotNull(EmailVerification::getVerifiedAt)
                .isNull(EmailVerification::getUsedAt)
                .gt(EmailVerification::getExpiresAt, now));
        if (updated != 1) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "邮箱验证凭据无效或已过期");
        }
    }

    private String hashCode(String email, String code) {
        return tokenHasher.hash(PURPOSE_REGISTER + ":" + email + ":" + code);
    }

    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
