package com.learnplatform.controller;

import tools.jackson.databind.JsonNode;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.understanding.QuestionUnderstandingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuestionUnderstandingControllerTest {
    private QuestionUnderstandingService service;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        service = mock(QuestionUnderstandingService.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(7L, "learner", "USER"), null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new QuestionUnderstandingController(service))
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver()).build();
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void exposesUnderstandingAndTutorPathsWithoutUnsubmittedAnswers() throws Exception {
        when(service.question(7L, 9L)).thenReturn(Map.of("questionId", 9, "options", List.of(Map.of("label", "A", "content", "x"))));
        mvc.perform(get("/api/understanding/questions/9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.options[0].label").value("A"))
                .andExpect(jsonPath("$.data.correctAnswer").doesNotExist());
        when(service.submit(eq(7L), eq(9L), any(JsonNode.class))).thenReturn(Map.of("attemptId", 11, "correct", false));
        mvc.perform(post("/api/understanding/questions/9/attempts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"B\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.attemptId").value(11))
                .andExpect(jsonPath("$.data.correctAnswer").doesNotExist());
        when(service.reveal(7L, 11L)).thenReturn(Map.of("correctAnswer", "A"));
        mvc.perform(post("/api/understanding/attempts/11/solution/reveal"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.correctAnswer").value("A"));
        when(service.tutorNext(7L, 11L)).thenReturn(Map.of("action", Map.of("type", "HINT", "level", 1)));
        mvc.perform(get("/api/understanding/attempts/11/tutor/next"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.action.level").value(1));
        when(service.tutorHint(eq(7L), eq(11L), any(JsonNode.class)))
                .thenReturn(Map.of("level", 1, "text", "看核心概念"));
        mvc.perform(post("/api/understanding/attempts/11/tutor/hints/next")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"00000000-0000-4000-8000-000000000001\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.level").value(1));
        when(service.tutorVizView(eq(7L), eq(11L), any(JsonNode.class)))
                .thenReturn(Map.of("context", "MISCONCEPTION", "viewed", true));
        mvc.perform(post("/api/understanding/attempts/11/tutor/viz-views")
                .contentType(MediaType.APPLICATION_JSON).content("{\"context\":\"MISCONCEPTION\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewed").value(true));
        when(service.variant(7L, 11L)).thenReturn(Map.of("variantId", 12, "stem", "变式"));
        mvc.perform(get("/api/understanding/attempts/11/variant"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.correctAnswer").doesNotExist());
        when(service.submitVariant(eq(7L), eq(12L), any(JsonNode.class)))
                .thenReturn(Map.of("variantAttemptId", 13, "assistance", "UNAIDED"));
        mvc.perform(post("/api/understanding/variants/12/attempts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"originAttemptId\":11,\"answer\":\"A\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.assistance").value("UNAIDED"));
        verify(service).submitVariant(eq(7L), eq(12L), any(JsonNode.class));
    }
}
