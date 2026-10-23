package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WriteConflictExceptionHandlerTest {
    @Test void databaseLockFailureReturnsAnExplicitRetryWithoutSqlDetails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ConflictController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/conflict")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1007))
                .andExpect(jsonPath("$.message").value("写入冲突，请重试"));
    }

    @RestController static class ConflictController {
        @GetMapping("/conflict") String write() {
            throw new CannotAcquireLockException("fixture SQL and internal database detail");
        }
    }
}
