package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.service.ai.AiCapacityException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AiCapacityExceptionHandler {
    @ExceptionHandler(AiCapacityException.class)
    public ResponseEntity<R<Void>> capacity(AiCapacityException exception) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(R.fail(exception.getCode(), exception.getMessage()));
    }
}
