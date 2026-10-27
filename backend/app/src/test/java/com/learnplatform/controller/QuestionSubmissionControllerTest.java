package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.HttpIdempotencyService;
import com.learnplatform.service.QuestionSubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionSubmissionControllerTest {
    @Mock private QuestionSubmissionService submissions;
    @Mock private HttpIdempotencyService idempotency;

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void mySubmissions_keepsCurrentUserAndPassesAllFilters() {
        CustomUserDetails user = new CustomUserDetails(7L, "learner", "USER");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        Page<QuestionSubmissionVO> page = new Page<>(1, 10, 0);
        when(submissions.getMySubmissions(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq("SINGLE_CHOICE"), org.mockito.ArgumentMatchers.eq("栈")))
                .thenReturn(page);

        new QuestionSubmissionController(submissions, idempotency)
                .mySubmissions(1, 10, 0, 3L, "SINGLE_CHOICE", " 栈 ");

        verify(submissions).getMySubmissions(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq("SINGLE_CHOICE"), org.mockito.ArgumentMatchers.eq("栈"));
    }
}
