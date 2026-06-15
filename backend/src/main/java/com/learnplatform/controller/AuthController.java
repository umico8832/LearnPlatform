package com.learnplatform.controller;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.R;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.CaptchaService;
import com.learnplatform.config.LoginRateLimitService;
import com.learnplatform.dto.*;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AuthService;
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
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, LoginRateLimitService rateLimitService, CaptchaService captchaService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.captchaService = captchaService;
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
        String clientIp = extractClientIp(httpRequest);

        // 校验验证码
        if (!captchaService.verifyCaptcha(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "验证码错误或已过期");
        }

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

    /**
     * 从请求中提取客户端真实 IP，支持反向代理场景。
     */
    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            // 取第一个 IP（客户端真实 IP）
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
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
