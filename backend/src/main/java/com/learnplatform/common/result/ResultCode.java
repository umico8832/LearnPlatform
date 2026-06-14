package com.learnplatform.common.result;

/**
 * 统一响应码枚举
 */
public enum ResultCode {

    SUCCESS(0, "success"),
    VALIDATION_ERROR(1001, "参数校验失败"),
    UNAUTHORIZED(1002, "未登录或Token无效"),
    FORBIDDEN(1003, "无权限"),
    NOT_FOUND(1004, "资源不存在"),
    BUSINESS_ERROR(1005, "业务异常"),
    QUOTA_EXCEEDED(1006, "调用额度已用完"),
    RATE_LIMITED(1007, "请求过于频繁，请稍后再试"),
    SYSTEM_ERROR(5000, "系统异常");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}