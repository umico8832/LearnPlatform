package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.*;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public R<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        UserVO userVO = authService.register(request);
        return R.ok(userVO);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return R.ok(response);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public R<UserVO> getCurrentUser() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        UserVO userVO = authService.getCurrentUser(userDetails.getUserId());
        return R.ok(userVO);
    }
}