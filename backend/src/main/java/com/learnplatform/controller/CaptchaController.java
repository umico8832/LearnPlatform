package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.config.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 验证码控制器
 */
@Tag(name = "验证码", description = "获取图形验证码")
@RestController
@RequestMapping("/api/auth")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @Operation(summary = "获取验证码", description = "返回验证码图片（base64）和验证码ID")
    @GetMapping("/captcha")
    public R<Map<String, String>> getCaptcha() {
        CaptchaService.CaptchaResult result = captchaService.generateCaptcha();
        Map<String, String> data = new HashMap<>();
        data.put("captchaId", result.getCaptchaId());
        data.put("image", result.getImage());
        return R.ok(data);
    }
}