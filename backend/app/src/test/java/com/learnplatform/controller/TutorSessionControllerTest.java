package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.TutorAgentRunVO;
import com.learnplatform.dto.TutorCheckResultVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.TutorAgentService;
import com.learnplatform.service.TutorSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TutorSessionControllerTest {
    @Mock private TutorSessionService sessions;
    @Mock private TutorAgentService agent;
    private MockMvc mockMvc;

    @BeforeEach void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserDetails(7L, "learner", "USER"), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        TutorSessionController controller = new TutorSessionController(sessions, agent);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    @Test void startsAndResumesAnOwnedTutorAgentRun() throws Exception {
        TutorAgentRunVO first = new TutorAgentRunVO();
        first.setRunKey("run");
        first.setStatus("WAITING_USER");
        when(agent.start(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("session"), argThat(value -> "解释一下".equals(value.getMessage()))))
                .thenReturn(first);

        mockMvc.perform(post("/api/my-courses/10/tutor-sessions/session/agent-runs")
                        .contentType("application/json").content("{\"message\":\"解释一下\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runKey").value("run"))
                .andExpect(jsonPath("$.data.status").value("WAITING_USER"));

        mockMvc.perform(post("/api/my-courses/10/tutor-sessions/session/agent-runs/run/messages")
                        .contentType("application/json").content("{\"message\":\"换个例子\"}"))
                .andExpect(status().isOk());
        verify(agent).resume(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("session"), org.mockito.ArgumentMatchers.eq("run"),
                argThat(value -> "换个例子".equals(value.getMessage())));
    }

    @Test void validatesQuestionsAndLoadsOnlyTheAuthenticatedRun() throws Exception {
        mockMvc.perform(post("/api/my-courses/10/tutor-sessions/session/agent-runs")
                        .contentType("application/json").content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/my-courses/10/tutor-sessions/session/agent-runs/run"))
                .andExpect(status().isOk());
        verify(agent).get(7L, 10L, "session", "run");
    }

    @Test void restoresTheTutorSessionThroughTheAuthenticatedOwner() throws Exception {
        mockMvc.perform(get("/api/my-courses/10/tutor-sessions/session"))
                .andExpect(status().isOk());
        verify(sessions).get(7L, 10L, "session");
    }

    @Test void submitsTutorCheckWithinTheCoursePath() throws Exception {
        TutorCheckResultVO result = new TutorCheckResultVO(); result.setCorrect(true);
        when(sessions.answer(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("session"),
                argThat(value -> "A".equals(value.getOptionId())))).thenReturn(result);

        mockMvc.perform(post("/api/my-courses/10/tutor-sessions/session/check")
                        .contentType("application/json").content("{\"optionId\":\"A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true));
        verify(sessions).answer(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("session"), argThat(value -> "A".equals(value.getOptionId())));
    }
}
