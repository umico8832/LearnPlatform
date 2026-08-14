package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamDraftConfirmRequest;
import com.learnplatform.dto.PrivateExamDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDraftReviewRequest;
import com.learnplatform.dto.PrivateExamDraftVO;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.entity.PrivateExamDraftQuestion;
import com.learnplatform.entity.PrivateExamImportDraft;
import com.learnplatform.entity.UserExamSource;
import com.learnplatform.mapper.PrivateExamDraftQuestionMapper;
import com.learnplatform.mapper.PrivateExamImportDraftMapper;
import com.learnplatform.mapper.UserExamSourceMapper;
import com.learnplatform.service.ai.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateExamDraftServiceTest {
    @Mock private PrivateExamImportService importService;
    @Mock private UserExamSourceMapper sourceMapper;
    @Mock private PrivateExamImportDraftMapper draftMapper;
    @Mock private PrivateExamDraftQuestionMapper questionMapper;
    @Mock private AiProvider aiProvider;
    @Mock private AiService aiService;
    private PrivateExamDraftService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new PrivateExamDraftService(importService, sourceMapper, draftMapper,
                questionMapper, aiProvider, aiService, objectMapper);
    }

    @Test
    void createsOwnerDraftWithoutEnablingPaper() {
        PrivateExamDraftCreateRequest request = createRequest();
        PrivateExamImportPreviewVO preview = preview(false);
        when(importService.preview(request)).thenReturn(preview);
        when(sourceMapper.insert(any())).thenAnswer(invocation -> {
            ((UserExamSource) invocation.getArgument(0)).setId(21L);
            return 1;
        });
        when(draftMapper.insert(any())).thenAnswer(invocation -> {
            ((PrivateExamImportDraft) invocation.getArgument(0)).setId(31L);
            return 1;
        });
        when(questionMapper.insert(any())).thenAnswer(invocation -> {
            ((PrivateExamDraftQuestion) invocation.getArgument(0)).setId(41L);
            return 1;
        });
        when(draftMapper.selectById(31L)).thenReturn(draft(7L, "DRAFT"));
        when(questionMapper.selectList(any())).thenReturn(List.of(question("PENDING", "PENDING")));

        PrivateExamDraftVO result = service.create(request, 7L);

        assertEquals("DRAFT", result.getStatus());
        ArgumentCaptor<PrivateExamDraftQuestion> captor = ArgumentCaptor.forClass(PrivateExamDraftQuestion.class);
        verify(questionMapper).insert(captor.capture());
        assertEquals("PENDING", captor.getValue().getGenerationStatus());
        assertEquals("PENDING", captor.getValue().getReviewStatus());
        verify(importService, never()).createConfirmedPaper(any(), any(), any(), any(), any(), any());
    }

    @Test
    void hidesDraftFromAnotherUser() {
        when(draftMapper.selectById(31L)).thenReturn(draft(7L, "DRAFT"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.get(31L, 8L));

        assertEquals("私有试卷草稿不存在", exception.getMessage());
        verify(questionMapper, never()).selectList(any());
    }

    @Test
    void validatesAndStoresAiSuggestionWithoutMarkingQuestionReviewed() {
        PrivateExamImportDraft draft = draft(7L, "DRAFT");
        PrivateExamDraftQuestion question = question("PENDING", "PENDING");
        when(draftMapper.selectById(31L)).thenReturn(draft);
        when(questionMapper.selectById(41L)).thenReturn(question);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(aiProvider.chat(any(), any())).thenReturn("{\"answerLabels\":[\"A\"],\"analysis\":\"栈遵循后进先出。\"}");

        service.generateAnswer(31L, 41L, 7L);

        ArgumentCaptor<PrivateExamDraftQuestion> captor = ArgumentCaptor.forClass(PrivateExamDraftQuestion.class);
        verify(questionMapper).updateById(captor.capture());
        assertEquals("[\"A\"]", captor.getValue().getAiAnswerJson());
        assertEquals("GENERATED", captor.getValue().getGenerationStatus());
        assertEquals("PENDING", captor.getValue().getReviewStatus());
        verify(aiService).checkDailyQuota(7L);
        verify(aiService).logCall(eq(7L), eq("private_exam_answer_generation"), eq(true), eq(null), any(Integer.class));
    }

    @Test
    void rejectsAiAnswerThatDoesNotMatchOptions() {
        when(draftMapper.selectById(31L)).thenReturn(draft(7L, "DRAFT"));
        when(questionMapper.selectById(41L)).thenReturn(question("PENDING", "PENDING"));
        when(aiProvider.chat(any(), any())).thenReturn("{\"answerLabels\":[\"Z\"],\"analysis\":\"错误输出\"}");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.generateAnswer(31L, 41L, 7L));

        assertEquals("AI 建议答案未通过结构校验，请重试", exception.getMessage());
        verify(questionMapper, never()).updateById(any());
    }

    @Test
    void reviewedAnswerMakesDraftReadyButDoesNotEnableIt() {
        PrivateExamImportDraft draft = draft(7L, "AI_GENERATED");
        PrivateExamDraftQuestion question = question("GENERATED", "PENDING");
        when(draftMapper.selectById(31L)).thenReturn(draft);
        when(questionMapper.selectById(41L)).thenReturn(question);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        PrivateExamDraftReviewRequest request = new PrivateExamDraftReviewRequest();
        request.setAnswerLabels(List.of("A"));
        request.setAnalysis("人工复核：栈遵循后进先出。");

        service.reviewQuestion(31L, 41L, request, 7L);

        assertEquals("REVIEWED", question.getReviewStatus());
        assertEquals("READY", draft.getStatus());
        verify(draftMapper).updateById(draft);
        verify(importService, never()).createConfirmedPaper(any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmsOnlyReadyDraftAndLinksEnabledPaper() {
        PrivateExamImportDraft draft = draft(7L, "READY");
        PrivateExamDraftQuestion question = question("GENERATED", "REVIEWED");
        question.setFinalAnswerJson("[\"A\"]");
        question.setFinalAnalysis("人工复核解析");
        UserExamSource source = new UserExamSource();
        source.setId(21L);
        source.setOwnerUserId(7L);
        when(draftMapper.selectOwnedForUpdate(31L, 7L)).thenReturn(draft);
        when(sourceMapper.selectById(21L)).thenReturn(source);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        ExamPaperVO paper = new ExamPaperVO();
        paper.setId(51L);
        when(importService.createConfirmedPaper(any(), any(), any(), any(), any(), eq(7L))).thenReturn(paper);
        PrivateExamDraftConfirmRequest request = new PrivateExamDraftConfirmRequest();
        request.setConfirmed(true);

        ExamPaperVO result = service.confirm(31L, request, 7L);

        assertEquals(51L, result.getId());
        assertEquals("CONFIRMED", draft.getStatus());
        assertEquals(51L, draft.getConfirmedPaperId());
        verify(draftMapper).updateById(draft);
    }

    @Test
    void repeatedConfirmationReturnsAlreadyEnabledPaper() {
        PrivateExamImportDraft draft = draft(7L, "CONFIRMED");
        draft.setConfirmedPaperId(51L);
        when(draftMapper.selectOwnedForUpdate(31L, 7L)).thenReturn(draft);
        ExamPaperVO paper = new ExamPaperVO();
        paper.setId(51L);
        when(importService.getConfirmedPaper(51L, 7L)).thenReturn(paper);
        PrivateExamDraftConfirmRequest request = new PrivateExamDraftConfirmRequest();
        request.setConfirmed(true);

        ExamPaperVO result = service.confirm(31L, request, 7L);

        assertEquals(51L, result.getId());
        verify(importService, never()).createConfirmedPaper(any(), any(), any(), any(), any(), any());
    }

    private PrivateExamDraftCreateRequest createRequest() {
        PrivateExamDraftCreateRequest request = new PrivateExamDraftCreateRequest();
        request.setTitle("AI 补全卷");
        request.setCourseId(10L);
        request.setDuration(30);
        request.setSourceName("answerless.txt");
        request.setSourceFormat("TEXT");
        request.setContent("题干：先进后出的数据结构是？");
        request.setExpectedContentHash("a".repeat(64));
        return request;
    }

    private PrivateExamImportPreviewVO preview(boolean complete) {
        PrivateExamImportPreviewVO preview = new PrivateExamImportPreviewVO();
        preview.setTitle("AI 补全卷");
        preview.setCourseId(10L);
        preview.setDuration(30);
        preview.setSourceName("answerless.txt");
        preview.setSourceFormat("TEXT");
        preview.setContentHash("a".repeat(64));
        preview.setRequiresAnswerReview(!complete);
        PrivateExamImportPreviewVO.QuestionPreview question = new PrivateExamImportPreviewVO.QuestionPreview();
        question.setContent("先进后出的数据结构是？");
        question.setQuestionType("SINGLE_CHOICE");
        question.setScore(1);
        question.setAnswerComplete(complete);
        question.setOptions(List.of(
                new PrivateExamImportPreviewVO.OptionPreview("A", "栈", complete),
                new PrivateExamImportPreviewVO.OptionPreview("B", "队列", false)));
        preview.setQuestions(List.of(question));
        preview.setQuestionCount(1);
        preview.setTotalScore(1);
        return preview;
    }

    private PrivateExamImportDraft draft(Long ownerId, String status) {
        PrivateExamImportDraft draft = new PrivateExamImportDraft();
        draft.setId(31L);
        draft.setOwnerUserId(ownerId);
        draft.setTitle("AI 补全卷");
        draft.setCourseId(10L);
        draft.setDuration(30);
        draft.setSourceRecordId(21L);
        draft.setStatus(status);
        return draft;
    }

    private PrivateExamDraftQuestion question(String generationStatus, String reviewStatus) {
        PrivateExamDraftQuestion question = new PrivateExamDraftQuestion();
        question.setId(41L);
        question.setDraftId(31L);
        question.setSortOrder(1);
        question.setContent("先进后出的数据结构是？");
        question.setQuestionType("SINGLE_CHOICE");
        question.setScore(1);
        question.setOptionsJson("[{\"label\":\"A\",\"content\":\"栈\"},{\"label\":\"B\",\"content\":\"队列\"}]");
        question.setGenerationStatus(generationStatus);
        question.setReviewStatus(reviewStatus);
        return question;
    }
}
