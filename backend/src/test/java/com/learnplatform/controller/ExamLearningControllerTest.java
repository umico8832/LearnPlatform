package com.learnplatform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.ExamLearningAnswerRequest;
import com.learnplatform.dto.ExamLearningAnswerResultVO;
import com.learnplatform.dto.ExamLearningSessionVO;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamImportConfirmRequest;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.dto.PrivateExamImportRequest;
import com.learnplatform.dto.PrivateExamDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDraftVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamPaperLearningService;
import com.learnplatform.service.ExamPaperService;
import com.learnplatform.service.ExamService;
import com.learnplatform.service.PrivateExamImportService;
import com.learnplatform.service.PrivateExamDraftService;
import com.learnplatform.service.PrivateExamContentLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExamLearningControllerTest {

    @Mock private ExamService examService;
    @Mock private ExamPaperService examPaperService;
    @Mock private ExamPaperLearningService learningService;
    @Mock private PrivateExamImportService privateExamImportService;
    @Mock private PrivateExamDraftService privateExamDraftService;
    @Mock private PrivateExamContentLifecycleService privateExamContentLifecycleService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ExamController controller = new ExamController(
                examService, examPaperService, learningService, privateExamImportService, privateExamDraftService,
                privateExamContentLifecycleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    @Test
    void startsAndReadsAuthenticatedLearningSession() throws Exception {
        ExamLearningSessionVO session = new ExamLearningSessionVO();
        session.setId(30L);
        session.setExamPaperId(2L);
        when(learningService.startSession(2L, 7L)).thenReturn(session);
        when(learningService.getSession(30L, 7L)).thenReturn(session);

        mockMvc.perform(post("/api/exam/papers/2/learning-sessions").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(30));
        mockMvc.perform(get("/api/exam/learning-sessions/30").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examPaperId").value(2));

        verify(learningService).startSession(2L, 7L);
        verify(learningService).getSession(30L, 7L);
    }

    @Test
    void submitsOneAnswerAndCompletesAuthenticatedSession() throws Exception {
        ExamLearningAnswerResultVO result = new ExamLearningAnswerResultVO();
        result.setQuestionId(10L);
        result.setCorrect(true);
        when(learningService.submitAnswer(
                org.mockito.ArgumentMatchers.eq(30L),
                org.mockito.ArgumentMatchers.any(ExamLearningAnswerRequest.class),
                org.mockito.ArgumentMatchers.eq(7L))).thenReturn(result);
        ExamLearningSessionVO completed = new ExamLearningSessionVO();
        completed.setId(30L);
        completed.setStatus(1);
        when(learningService.completeSession(30L, 7L)).thenReturn(completed);

        ExamLearningAnswerRequest request = new ExamLearningAnswerRequest();
        request.setQuestionId(10L);
        request.setUserAnswer("A");
        mockMvc.perform(post("/api/exam/learning-sessions/30/answers")
                        .with(mockUser(7L))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true));
        mockMvc.perform(post("/api/exam/learning-sessions/30/complete").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));

        verify(learningService).completeSession(30L, 7L);
    }

    @Test
    void readsAuthenticatedTimedExamSessionWithoutAnswerDetails() throws Exception {
        ExamRecordVO session = new ExamRecordVO();
        session.setId(40L);
        session.setExamPaperId(2L);
        session.setStatus(0);
        when(examService.getExamSession(40L, 7L)).thenReturn(session);

        mockMvc.perform(get("/api/exam/records/40/session").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(40))
                .andExpect(jsonPath("$.data.examPaperId").value(2))
                .andExpect(jsonPath("$.data.answers").doesNotExist());

        verify(examService).getExamSession(40L, 7L);
    }

    @Test
    void previewsThenConfirmsPrivateImportForAuthenticatedOwner() throws Exception {
        PrivateExamImportRequest previewRequest = privateImportRequest();
        PrivateExamImportPreviewVO preview = new PrivateExamImportPreviewVO();
        preview.setContentHash("a".repeat(64));
        preview.setQuestionCount(1);
        when(privateExamImportService.preview(org.mockito.ArgumentMatchers.any())).thenReturn(preview);

        mockMvc.perform(post("/api/exam/private-papers/import/preview")
                        .with(mockUser(7L)).contentType("application/json")
                        .content(objectMapper.writeValueAsString(previewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionCount").value(1));

        PrivateExamImportConfirmRequest confirmRequest = new PrivateExamImportConfirmRequest();
        copyPrivateImport(previewRequest, confirmRequest);
        confirmRequest.setExpectedContentHash("a".repeat(64));
        confirmRequest.setConfirmed(true);
        ExamPaperVO paper = new ExamPaperVO();
        paper.setId(9L);
        when(privateExamImportService.confirm(
                org.mockito.ArgumentMatchers.any(PrivateExamImportConfirmRequest.class),
                org.mockito.ArgumentMatchers.eq(7L))).thenReturn(paper);
        mockMvc.perform(post("/api/exam/private-papers/import/confirm")
                        .with(mockUser(7L)).contentType("application/json")
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));
        verify(privateExamImportService).confirm(
                org.mockito.ArgumentMatchers.any(PrivateExamImportConfirmRequest.class),
                org.mockito.ArgumentMatchers.eq(7L));
    }

    @Test
    void createsGeneratesReviewsAndConfirmsPrivateDraftForAuthenticatedOwner() throws Exception {
        PrivateExamDraftCreateRequest createRequest = new PrivateExamDraftCreateRequest();
        copyPrivateImport(privateImportRequest(), createRequest);
        createRequest.setExpectedContentHash("a".repeat(64));
        PrivateExamDraftVO draft = new PrivateExamDraftVO();
        draft.setId(31L);
        draft.setStatus("DRAFT");
        when(privateExamDraftService.create(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(7L))).thenReturn(draft);
        when(privateExamDraftService.generateAnswer(31L, 41L, 7L)).thenReturn(draft);
        when(privateExamDraftService.reviewQuestion(org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.eq(41L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(7L))).thenReturn(draft);
        ExamPaperVO paper = new ExamPaperVO();
        paper.setId(51L);
        when(privateExamDraftService.confirm(org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(7L))).thenReturn(paper);

        mockMvc.perform(post("/api/exam/private-papers/drafts").with(mockUser(7L))
                        .contentType("application/json").content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(31));
        mockMvc.perform(post("/api/exam/private-papers/drafts/31/questions/41/ai-answer").with(mockUser(7L)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/exam/private-papers/drafts/31/questions/41/review").with(mockUser(7L))
                        .contentType("application/json")
                        .content("{\"answerLabels\":[\"A\"],\"analysis\":\"人工复核解析\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/exam/private-papers/drafts/31/confirm").with(mockUser(7L))
                        .contentType("application/json").content("{\"confirmed\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(51));
    }

    @Test
    void deletesOwnedPrivateDraftAndPaperThroughUserEndpoints() throws Exception {
        mockMvc.perform(delete("/api/exam/private-papers/drafts/31").with(mockUser(7L)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/exam/private-papers/51").with(mockUser(7L)))
                .andExpect(status().isOk());

        verify(privateExamContentLifecycleService).deleteDraft(31L, 7L);
        verify(privateExamContentLifecycleService).deletePaper(51L, 7L);
    }

    private PrivateExamImportRequest privateImportRequest() {
        PrivateExamImportRequest request = new PrivateExamImportRequest();
        request.setTitle("我的试卷");
        request.setCourseId(10L);
        request.setDuration(30);
        request.setSourceName("paper.txt");
        request.setSourceFormat("TEXT");
        request.setContent("题型：单选题\n题干：示例\n选项：\nA. 是\nB. 否\n答案：A");
        return request;
    }

    private void copyPrivateImport(PrivateExamImportRequest source, PrivateExamImportRequest target) {
        target.setTitle(source.getTitle());
        target.setCourseId(source.getCourseId());
        target.setDuration(source.getDuration());
        target.setSourceName(source.getSourceName());
        target.setSourceFormat(source.getSourceFormat());
        target.setContent(source.getContent());
    }

    private RequestPostProcessor mockUser(Long userId) {
        return request -> {
            CustomUserDetails details = new CustomUserDetails(userId, "testuser", "USER");
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    details, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            return request;
        };
    }
}
