package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank(message = "用户名或邮箱不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "请完成人机验证")
    private String turnstileToken;

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getTurnstileToken() { return turnstileToken; }
    public void setTurnstileToken(String turnstileToken) { this.turnstileToken = turnstileToken; }
}
