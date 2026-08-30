package com.learnplatform.controller;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.R;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.security.LoginRateLimitService;
import com.learnplatform.dto.*;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AuthService;
import com.learnplatform.service.ClientIpResolver;
import com.learnplatform.service.EmailVerificationService;
import com.learnplatform.service.PasswordResetService;
import com.learnplatform.service.TurnstileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Tag(name = "认证管理", description = "用户注册、登录、获取当前用户信息")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final LoginRateLimitService rateLimitService;
    private final TurnstileService turnstileService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, LoginRateLimitService rateLimitService,
                          TurnstileService turnstileService, EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.turnstileService = turnstileService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * 用户注册
     */
    @Operation(summary = "用户注册", description = "新用户注册账号")
    @PostMapping("/register")
    public R<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        UserVO userVO = authService.register(request);
        return R.ok(userVO);
    }

    /**
     * 用户登录
     */
    @Operation(summary = "用户登录", description = "用户名密码登录，返回JWT Token")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                  HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        turnstileService.verify(request.getTurnstileToken(), clientIp);

        // 检查 IP 限流
        if (rateLimitService.isBlocked(clientIp)) {
            long remaining = rateLimitService.getRemainingBlockSeconds(clientIp);
            log.warn("IP {} 已被限流，剩余 {} 秒", clientIp, remaining);
            throw new BusinessException(ResultCode.RATE_LIMITED,
                    "登录失败次数过多，请 " + remaining + " 秒后再试");
        }

        try {
            LoginResponse response = authService.login(request);
            // 登录成功清除失败记录
            rateLimitService.clearRecord(clientIp);
            return R.ok(response);
        } catch (BusinessException e) {
            // 仅对认证失败（用户名或密码错误）记录失败次数
            if (e.getCode() == ResultCode.UNAUTHORIZED.getCode()) {
                rateLimitService.recordFailure(clientIp);
            }
            throw e;
        }
    }

    @PostMapping("/email/register-code")
    public R<Void> sendRegisterCode(@Valid @RequestBody SendEmailCodeRequest request,
                                    HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        turnstileService.verify(request.getTurnstileToken(), clientIp);
        emailVerificationService.sendRegistrationCode(
                request.getEmail(), clientIp, httpRequest.getHeader("User-Agent"));
        return R.ok(null);
    }

    @PostMapping("/email/verify-register-code")
    public R<VerificationTicketResponse> verifyRegisterCode(
            @Valid @RequestBody VerifyEmailCodeRequest request) {
        return R.ok(emailVerificationService.verifyRegistrationCode(request.getEmail(), request.getCode()));
    }

    @PostMapping("/password/forgot")
    public R<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                  HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        turnstileService.verify(request.getTurnstileToken(), clientIp);
        passwordResetService.requestReset(request.getEmail(), clientIp);
        return R.ok(null);
    }

    @GetMapping("/password/reset/validate")
    public R<String> validateResetToken(@RequestParam String token) {
        return R.ok(passwordResetService.validateToken(token));
    }

    @PostMapping("/password/reset")
    public R<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getPassword());
        return R.ok(null);
    }

    /**
     * 获取当前登录用户信息
     */
    @Operation(summary = "获取当前用户", description = "根据JWT Token获取当前登录用户信息")
    @GetMapping("/me")
    public R<UserVO> getCurrentUser() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        UserVO userVO = authService.getCurrentUser(userDetails.getUserId());
        return R.ok(userVO);
    }

    /**
     * 修改个人信息（昵称）
     */
    @Operation(summary = "修改个人信息", description = "修改当前用户的昵称")
    @PutMapping("/profile")
    public R<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        UserVO userVO = authService.updateProfile(userDetails.getUserId(), request);
        return R.ok(userVO);
    }

    /**
     * 修改密码
     */
    @Operation(summary = "修改密码", description = "修改当前用户的登录密码，需验证原密码")
    @PutMapping("/password")
    public R<Void> updatePassword(@Valid @RequestBody UpdatePasswordRequest request) {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        authService.updatePassword(userDetails.getUserId(), request);
        return R.ok(null);
    }
}
