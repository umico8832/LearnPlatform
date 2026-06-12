package com.learnplatform.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一响应码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "success"),
    VALIDATION_ERROR(1001, "参数校验失败"),
    UNAUTHORIZED(1002, "未登录或Token无效"),
    FORBIDDEN(1003, "无权限"),
    NOT_FOUND(1004, "资源不存在"),
    BUSINESS_ERROR(1005, "业务异常"),
    SYSTEM_ERROR(5000, "系统异常");

    private final int code;
    private final String message;
}