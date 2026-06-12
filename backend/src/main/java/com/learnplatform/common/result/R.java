package com.learnplatform.common.result;

import java.io.Serializable;

/**
 * 统一响应体
 *
 * @param <T> 数据类型
 */
public class R<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    private R() {
    }

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    /**
     * 成功（无数据）
     */
    public static <T> R<T> ok() {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功（带数据）
     */
    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功（带消息和数据）
     */
    public static <T> R<T> ok(String message, T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 失败（使用 ResultCode）
     */
    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 失败（使用 ResultCode 和自定义消息）
     */
    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return new R<>(resultCode.getCode(), message, null);
    }

    /**
     * 失败（自定义 code 和消息）
     */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    /**
     * 业务异常
     */
    public static <T> R<T> businessError(String message) {
        return new R<>(ResultCode.BUSINESS_ERROR.getCode(), message, null);
    }
}