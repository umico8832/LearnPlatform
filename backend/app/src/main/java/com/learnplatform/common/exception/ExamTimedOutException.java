package com.learnplatform.common.exception;

import com.learnplatform.common.result.ResultCode;

/**
 * 考试超时异常。抛出该异常时需要保留已写入的超时状态。
 */
public class ExamTimedOutException extends BusinessException {

    public ExamTimedOutException() {
        super(ResultCode.BUSINESS_ERROR, "考试已超时");
    }
}
