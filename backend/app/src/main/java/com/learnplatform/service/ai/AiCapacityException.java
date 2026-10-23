package com.learnplatform.service.ai;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;

public final class AiCapacityException extends BusinessException {
    public AiCapacityException() { super(ResultCode.RATE_LIMITED); }
}
