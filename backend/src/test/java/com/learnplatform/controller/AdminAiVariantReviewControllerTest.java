package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.AiVariantReviewRequest;
import com.learnplatform.dto.AiVariantReviewVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.AiVariantReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAiVariantReviewControllerTest {
    @Mock private AiVariantReviewService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAiVariantReviewController(service))
                .setCustomArgumentResolvers(new AdminUserResolver()).build();
    }

    @Test
    void listsAndReviewsVariantsAsAuthenticatedAdmin() throws Exception {
        AiVariantReviewVO item = new AiVariantReviewVO();
        item.setId(12L);
        item.setReviewStatus("PENDING");
        Page<AiVariantReviewVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(item));
        when(service.list("PENDING", 1, 10)).thenReturn(page);
        AiVariantReviewVO approved = new AiVariantReviewVO();
        approved.setId(12L);
        approved.setReviewStatus("APPROVED");
        when(service.review(eq(12L), any(AiVariantReviewRequest.class), eq(3L))).thenReturn(approved);

        mockMvc.perform(get("/api/admin/ai-variant-reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].reviewStatus").value("PENDING"));
        mockMvc.perform(post("/api/admin/ai-variant-reviews/12")
                        .contentType("application/json")
                        .content("{\"decision\":\"APPROVE\",\"reviewNote\":\"核验通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"));
        verify(service).review(eq(12L), any(AiVariantReviewRequest.class), eq(3L));
    }

    private static class AdminUserResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return new CustomUserDetails(3L, "admin", "ADMIN");
        }
    }
}
