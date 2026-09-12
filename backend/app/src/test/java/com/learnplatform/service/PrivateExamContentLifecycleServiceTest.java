package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.PrivateExamImportDraft;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.PrivateExamContentLifecycleMapper;
import com.learnplatform.mapper.PrivateExamImportDraftMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateExamContentLifecycleServiceTest {
    @Mock private PrivateExamImportDraftMapper draftMapper;
    @Mock private ExamPaperMapper paperMapper;
    @Mock private PrivateExamContentLifecycleMapper lifecycleMapper;
    private PrivateExamContentLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new PrivateExamContentLifecycleService(draftMapper, paperMapper, lifecycleMapper);
    }

    @Test
    void deletesOwnedUnconfirmedDraftAndUnreferencedSource() {
        PrivateExamImportDraft draft = draft(7L, "REVIEWING");
        when(draftMapper.selectOwnedForUpdate(31L, 7L)).thenReturn(draft);
        when(lifecycleMapper.deleteDraft(31L, 7L)).thenReturn(1);

        service.deleteDraft(31L, 7L);

        verify(lifecycleMapper).deleteDraftQuestions(31L);
        verify(lifecycleMapper).deleteDraft(31L, 7L);
        verify(lifecycleMapper).deleteSourceIfUnreferenced(21L, 7L);
    }

    @Test
    void confirmedDraftMustBeDeletedThroughItsPaper() {
        when(draftMapper.selectOwnedForUpdate(31L, 7L)).thenReturn(draft(7L, "CONFIRMED"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteDraft(31L, 7L));

        assertEquals("已启用草稿请通过私有试卷删除", exception.getMessage());
        verify(lifecycleMapper, never()).deleteDraftQuestions(31L);
    }

    @Test
    void hidesDraftFromAnotherOwnerDuringDelete() {
        when(draftMapper.selectOwnedForUpdate(31L, 8L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteDraft(31L, 8L));

        assertEquals("私有试卷草稿不存在", exception.getMessage());
    }

    @Test
    void rejectsPrivatePaperDeletionAfterLearningFactsExist() {
        ExamPaper paper = paper(7L);
        when(paperMapper.selectByIdForUpdate(51L)).thenReturn(paper);
        when(lifecycleMapper.countPaperReferences(51L)).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deletePaper(51L, 7L));

        assertEquals("私有试卷已有考试、学习记录或衍生内容，不能删除", exception.getMessage());
        verify(lifecycleMapper, never()).deletePrivatePaper(51L, 7L);
    }

    @Test
    void physicallyDeletesUnusedOwnedPrivatePaperQuestionsAndSource() {
        ExamPaper paper = paper(7L);
        when(paperMapper.selectByIdForUpdate(51L)).thenReturn(paper);
        when(lifecycleMapper.countPaperReferences(51L)).thenReturn(0L);
        when(lifecycleMapper.selectQuestionIds(51L)).thenReturn(List.of(61L, 62L));
        when(lifecycleMapper.countQuestionReferences(51L, List.of(61L, 62L))).thenReturn(0L);
        when(lifecycleMapper.deletePrivatePaper(51L, 7L)).thenReturn(1);

        service.deletePaper(51L, 7L);

        verify(lifecycleMapper).deleteExamQuestions(51L);
        verify(lifecycleMapper).deleteQuestionOptions(List.of(61L, 62L));
        verify(lifecycleMapper).deleteQuestionKnowledgePoints(List.of(61L, 62L));
        verify(lifecycleMapper).deleteQuestions(List.of(61L, 62L));
        verify(lifecycleMapper).deletePrivatePaper(51L, 7L);
        verify(lifecycleMapper).deleteConfirmedDraftQuestions(51L, 7L);
        verify(lifecycleMapper).deleteConfirmedDrafts(51L, 7L);
        verify(lifecycleMapper).deleteSourceIfUnreferenced(21L, 7L);
    }

    @Test
    void hidesPublicOrAnotherUsersPaperDuringDelete() {
        when(paperMapper.selectByIdForUpdate(51L)).thenReturn(paper(8L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deletePaper(51L, 7L));

        assertEquals("私有试卷不存在", exception.getMessage());
        verify(lifecycleMapper, never()).countPaperReferences(51L);
    }

    private PrivateExamImportDraft draft(Long ownerId, String status) {
        PrivateExamImportDraft draft = new PrivateExamImportDraft();
        draft.setId(31L);
        draft.setOwnerUserId(ownerId);
        draft.setSourceRecordId(21L);
        draft.setStatus(status);
        return draft;
    }

    private ExamPaper paper(Long ownerId) {
        ExamPaper paper = new ExamPaper();
        paper.setId(51L);
        paper.setOwnerUserId(ownerId);
        paper.setVisibility("PRIVATE");
        paper.setPaperType("USER_PRIVATE");
        paper.setSourceRecordId(21L);
        paper.setDeleted(0);
        return paper;
    }
}
