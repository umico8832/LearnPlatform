package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.PrivateExamImportDraft;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.PrivateExamContentLifecycleMapper;
import com.learnplatform.mapper.PrivateExamImportDraftMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PrivateExamContentLifecycleService {
    private final PrivateExamImportDraftMapper draftMapper;
    private final ExamPaperMapper paperMapper;
    private final PrivateExamContentLifecycleMapper lifecycleMapper;

    public PrivateExamContentLifecycleService(PrivateExamImportDraftMapper draftMapper,
                                              ExamPaperMapper paperMapper,
                                              PrivateExamContentLifecycleMapper lifecycleMapper) {
        this.draftMapper = draftMapper;
        this.paperMapper = paperMapper;
        this.lifecycleMapper = lifecycleMapper;
    }

    @Transactional
    public void deleteDraft(Long draftId, Long userId) {
        PrivateExamImportDraft draft = draftMapper.selectOwnedForUpdate(draftId, userId);
        if (draft == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "私有试卷草稿不存在");
        }
        if ("CONFIRMED".equals(draft.getStatus())) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "已启用草稿请通过私有试卷删除");
        }
        lifecycleMapper.deleteDraftQuestions(draftId);
        if (lifecycleMapper.deleteDraft(draftId, userId) != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "私有试卷草稿删除失败");
        }
        lifecycleMapper.deleteSourceIfUnreferenced(draft.getSourceRecordId(), userId);
    }

    @Transactional
    public void deletePaper(Long paperId, Long userId) {
        ExamPaper paper = paperMapper.selectByIdForUpdate(paperId);
        if (!isOwnedPrivatePaper(paper, userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "私有试卷不存在");
        }
        if (lifecycleMapper.countPaperReferences(paperId) > 0) {
            throw referencedPaper();
        }
        List<Long> questionIds = lifecycleMapper.selectQuestionIds(paperId);
        if (!questionIds.isEmpty() && lifecycleMapper.countQuestionReferences(paperId, questionIds) > 0) {
            throw referencedPaper();
        }

        lifecycleMapper.deleteExamQuestions(paperId);
        if (!questionIds.isEmpty()) {
            lifecycleMapper.deleteQuestionOptions(questionIds);
            lifecycleMapper.deleteQuestionKnowledgePoints(questionIds);
            lifecycleMapper.deleteQuestions(questionIds);
        }
        if (lifecycleMapper.deletePrivatePaper(paperId, userId) != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "私有试卷删除失败");
        }
        lifecycleMapper.deleteConfirmedDraftQuestions(paperId, userId);
        lifecycleMapper.deleteConfirmedDrafts(paperId, userId);
        lifecycleMapper.deleteSourceIfUnreferenced(paper.getSourceRecordId(), userId);
    }

    private boolean isOwnedPrivatePaper(ExamPaper paper, Long userId) {
        return paper != null && Integer.valueOf(0).equals(paper.getDeleted())
                && "PRIVATE".equals(paper.getVisibility())
                && "USER_PRIVATE".equals(paper.getPaperType())
                && userId.equals(paper.getOwnerUserId());
    }

    private BusinessException referencedPaper() {
        return new BusinessException(ResultCode.VALIDATION_ERROR,
                "私有试卷已有考试、学习记录或衍生内容，不能删除");
    }
}
