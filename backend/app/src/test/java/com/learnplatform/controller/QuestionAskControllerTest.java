package com.learnplatform.controller;

import tools.jackson.databind.JsonNode;
import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.dto.QuestionAskResponse;
import com.learnplatform.service.understanding.QuestionAskService;
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
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuestionAskControllerTest {
    QuestionAskService service;
    MockMvc mvc;
    QuestionAskService.Job job;

    @BeforeEach void setup() {
        service = mock(QuestionAskService.class);
        job = new QuestionAskService.Job(7L, 11L, 9L, "00000000-0000-4000-8000-000000000001",
                "问题", null, java.util.UUID.randomUUID(), 190000L, new Cancellation());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(7L, "learner", "USER"), null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new QuestionAskController(service, Runnable::run))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver()).build();
        when(service.prepare(eq(7L), eq(11L), any(JsonNode.class))).thenReturn(job);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void exposesStatusHistoryStopAndValidatedStreamEvents() throws Exception {
        when(service.status(7L, 11L)).thenReturn(new QuestionAskResponse.Status(true, null, 4, 49, List.of()));
        mvc.perform(get("/api/understanding/attempts/11/ask"))
                .andExpect(jsonPath("$.data.available").value(true)).andExpect(jsonPath("$.data.records").isArray());
        when(service.stop(7L, 11L, job.requestId())).thenReturn(Map.of("stopped", true));
        mvc.perform(delete("/api/understanding/attempts/11/ask/" + job.requestId()))
                .andExpect(jsonPath("$.data.stopped").value(true));
        when(service.execute(eq(job), any())).thenAnswer(call -> {
            Consumer<String> consumer = call.getArgument(1);
            consumer.accept("安全正文");
            return new QuestionAskResponse.Done(job.requestId(), 4, 49, null);
        });
        var response = mvc.perform(post("/api/understanding/attempts/11/ask/stream")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(response)).andExpect(status().isOk())
                .andExpect(content().string(containsString("event:content")))
                .andExpect(content().string(containsString("event:done")));
    }

    @Test void mapsPreflightAndStreamingErrorsWithoutUpstreamDetails() throws Exception {
        mvc.perform(post("/api/understanding/attempts/11/ask/stream")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        when(service.prepare(eq(7L), eq(11L), any(JsonNode.class)))
                .thenThrow(new BusinessException(ResultCode.ASK_UNAVAILABLE));
        mvc.perform(post("/api/understanding/attempts/11/ask/stream")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).content("{}"))
                .andExpect(jsonPath("$.code").value(1010));
        when(service.prepare(eq(7L), eq(11L), any(JsonNode.class))).thenReturn(job);
        when(service.execute(eq(job), any())).thenThrow(new BusinessException(ResultCode.ASK_TIMEOUT));
        var response = mvc.perform(post("/api/understanding/attempts/11/ask/stream")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        mvc.perform(asyncDispatch(response)).andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("1013")));
    }
}
