package com.learnplatform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.ExamLearningAnswerRequest;
import com.learnplatform.dto.ExamLearningAnswerResultVO;
import com.learnplatform.dto.ExamLearningSessionVO;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamImportConfirmRequest;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.dto.PrivateExamImportRequest;
import com.learnplatform.dto.PrivateExamDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDraftVO;
import com.learnplatform.dto.PrivateExamStorageUsageVO;
import com.learnplatform.dto.exam.PrivateExamSourceFile;
import com.learnplatform.dto.PrivateExamSourceStorageItemVO;
import com.learnplatform.dto.PrivateExamPdfRequest;
import com.learnplatform.dto.PrivateExamDocxRequest;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.ExamPaperLearningService;
import com.learnplatform.service.PrivateExamImportService;
import com.learnplatform.service.PrivateExamDraftService;
import com.learnplatform.service.PrivateExamContentLifecycleService;
import com.learnplatform.service.PrivateExamPdfImportService;
import com.learnplatform.service.PrivateExamDocxImportService;
import com.learnplatform.service.PrivateExamSourceFileService;
import com.learnplatform.service.PrivateExamSourceStorageService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExamLearningControllerTest {

    @Mock private ExamPaperLearningService learningService;
    @Mock private PrivateExamImportService privateExamImportService;
    @Mock private PrivateExamDraftService privateExamDraftService;
    @Mock private PrivateExamContentLifecycleService privateExamContentLifecycleService;
    @Mock private PrivateExamPdfImportService privateExamPdfImportService;
    @Mock private PrivateExamDocxImportService privateExamDocxImportService;
    @Mock private PrivateExamSourceFileService privateExamSourceFileService;
    @Mock private PrivateExamSourceStorageService privateExamSourceStorageService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ExamPaperLearningController learningController =
                new ExamPaperLearningController(learningService);
        PrivateExamImportController importController = new PrivateExamImportController(
                privateExamImportService,
                privateExamDraftService,
                privateExamPdfImportService,
                privateExamDocxImportService);
        PrivateExamDraftController draftController = new PrivateExamDraftController(
                privateExamDraftService,
                privateExamContentLifecycleService);
        PrivateExamSourceController sourceController = new PrivateExamSourceController(
                privateExamImportService,
                privateExamContentLifecycleService,
                privateExamSourceFileService,
                privateExamSourceStorageService);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        learningController,
                        importController,
                        draftController,
                        sourceController)
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

    @Test
    void previewsTextPdfThroughMultipartEndpoint() throws Exception {
        PrivateExamPdfRequest metadata = new PrivateExamPdfRequest();
        metadata.setTitle("PDF 试卷");
        metadata.setCourseId(10L);
        metadata.setDuration(30);
        PrivateExamImportPreviewVO preview = new PrivateExamImportPreviewVO();
        preview.setQuestionCount(1);
        when(privateExamPdfImportService.preview(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(preview);
        MockMultipartFile metadataPart = new MockMultipartFile("metadata", "", "application/json",
                objectMapper.writeValueAsBytes(metadata));
        MockMultipartFile file = new MockMultipartFile("file", "paper.pdf", "application/pdf", "%PDF-test".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        "/api/exam/private-papers/import/pdf/preview")
                        .file(metadataPart).file(file).with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionCount").value(1));
    }

    @Test
    void previewsDocxThroughMultipartEndpoint() throws Exception {
        PrivateExamDocxRequest metadata = new PrivateExamDocxRequest();
        metadata.setTitle("DOCX 试卷");
        metadata.setCourseId(10L);
        metadata.setDuration(30);
        PrivateExamImportPreviewVO preview = new PrivateExamImportPreviewVO();
        preview.setQuestionCount(1);
        when(privateExamDocxImportService.preview(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(preview);
        MockMultipartFile metadataPart = new MockMultipartFile("metadata", "", "application/json",
                objectMapper.writeValueAsBytes(metadata));
        MockMultipartFile file = new MockMultipartFile("file", "paper.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "PK-test".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        "/api/exam/private-papers/import/docx/preview")
                        .file(metadataPart).file(file).with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionCount").value(1));
    }

    @Test
    void downloadsOwnedPaperAndDraftSourceFilesWithPrivateHeaders() throws Exception {
        byte[] pdf = "%PDF-source".getBytes();
        byte[] docx = "PK-source".getBytes();
        when(privateExamSourceFileService.getForPaper(51L, 7L))
                .thenReturn(new PrivateExamSourceFile("试卷.pdf", "application/pdf", pdf));
        when(privateExamSourceFileService.getForDraft(31L, 7L)).thenReturn(new PrivateExamSourceFile(
                "paper.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx));

        mockMvc.perform(get("/api/exam/private-papers/51/source/file").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdf))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mockMvc.perform(get("/api/exam/private-papers/drafts/31/source/file").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(docx))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void returnsAuthenticatedOwnerSourceStorageUsage() throws Exception {
        PrivateExamStorageUsageVO usage = new PrivateExamStorageUsageVO();
        usage.setUsedBytes(1024L);
        usage.setLimitBytes(104857600L);
        usage.setRemainingBytes(104856576L);
        usage.setFileCount(2L);
        when(privateExamSourceStorageService.getUsage(7L)).thenReturn(usage);

        mockMvc.perform(get("/api/exam/private-papers/source-storage").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedBytes").value(1024))
                .andExpect(jsonPath("$.data.limitBytes").value(104857600))
                .andExpect(jsonPath("$.data.fileCount").value(2));
    }

    @Test
    void listsAuthenticatedOwnerSourceStorageFiles() throws Exception {
        PrivateExamSourceStorageItemVO item = new PrivateExamSourceStorageItemVO();
        item.setId(11L);
        item.setSourceName("paper.pdf");
        item.setSourceFormat("PDF");
        item.setSourceSize(1024L);
        item.setAssociationType("PAPER");
        item.setAssociationId(51L);
        item.setAssociationTitle("我的试卷");
        Page<PrivateExamSourceStorageItemVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(item));
        when(privateExamSourceStorageService.listFiles(7L, 1, 10)).thenReturn(page);

        mockMvc.perform(get("/api/exam/private-papers/source-storage/files")
                        .param("pageNum", "1").param("pageSize", "10").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].sourceName").value("paper.pdf"))
                .andExpect(jsonPath("$.data.records[0].associationType").value("PAPER"));
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
