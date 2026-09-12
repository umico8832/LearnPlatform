package com.learnplatform.common.exception;

public class OAuthLoginException extends RuntimeException {
    private final String errorCode;

    public OAuthLoginException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
