package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamDraftConfirmRequest;
import com.learnplatform.dto.PrivateExamDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDraftReviewRequest;
import com.learnplatform.dto.PrivateExamDraftVO;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.entity.PrivateExamDraftQuestion;
import com.learnplatform.entity.PrivateExamImportDraft;
import com.learnplatform.entity.UserExamSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PrivateExamDraftService {
    private final PrivateExamImportService importService;
    private final PrivateExamDraftDataService dataService;
    private final PrivateExamDraftAnswerService answerService;

    public PrivateExamDraftService(PrivateExamImportService importService,
                                   PrivateExamDraftDataService dataService,
                                   PrivateExamDraftAnswerService answerService) {
        this.importService = importService;
        this.dataService = dataService;
        this.answerService = answerService;
    }

    @Transactional
    public PrivateExamDraftVO create(PrivateExamDraftCreateRequest request, Long userId) {
        return createFromPreview(request, userId, importService.preview(request), null, null);
    }

    @Transactional
    public PrivateExamDraftVO createWithSourceHash(PrivateExamDraftCreateRequest request, Long userId,
                                                    String sourceHash) {
        return createFromPreview(request, userId, importService.previewWithSourceHash(request, sourceHash),
                null, null);
    }

    @Transactional
    public PrivateExamDraftVO createWithSourceFile(PrivateExamDraftCreateRequest request, Long userId,
                                                    String sourceHash, byte[] sourceFile,
                                                    String sourceMediaType) {
        return createFromPreview(request, userId, importService.previewWithSourceHash(request, sourceHash),
                sourceFile, sourceMediaType);
    }

    private PrivateExamDraftVO createFromPreview(PrivateExamDraftCreateRequest request, Long userId,
                                                  PrivateExamImportPreviewVO preview,
                                                  byte[] sourceFile, String sourceMediaType) {
        if (!preview.getContentHash().equalsIgnoreCase(request.getExpectedContentHash())) {
            throw validation("原始资料已变化，请重新预览");
        }
        if (!Boolean.TRUE.equals(preview.getRequiresAnswerReview())) {
            throw validation("答案已完整，请直接确认导入");
        }
        return dataService.create(request, userId, preview, sourceFile, sourceMediaType);
    }

    public PrivateExamDraftVO get(Long draftId, Long userId) {
        return dataService.get(draftId, userId);
    }

    public List<PrivateExamDraftVO> listActive(Long userId) {
        return dataService.listActive(userId);
    }

    public PrivateExamDraftVO generateAnswer(Long draftId, Long questionId, Long userId) {
        return answerService.generate(draftId, questionId, userId);
    }

    @Transactional
    public PrivateExamDraftVO reviewQuestion(Long draftId, Long questionId,
                                             PrivateExamDraftReviewRequest request, Long userId) {
        PrivateExamImportDraft draft = dataService.mutableDraft(draftId, userId);
        PrivateExamDraftQuestion question = dataService.ownedQuestion(draftId, questionId);
        if ("PENDING".equals(question.getGenerationStatus())) {
            throw validation("该题必须先生成 AI 答案与解析");
        }
        List<String> answers = dataService.normalizeAndValidateAnswers(request.getAnswerLabels(), question);
        question.setFinalAnswerJson(dataService.writeJson(answers));
        question.setFinalAnalysis(request.getAnalysis().trim());
        question.setReviewStatus("REVIEWED");
        dataService.updateQuestion(question);

        List<PrivateExamDraftQuestion> questions = dataService.questions(draftId);
        boolean ready = questions.stream().allMatch(item -> "REVIEWED".equals(item.getReviewStatus()));
        draft.setStatus(ready ? "READY" : "REVIEWING");
        dataService.updateDraft(draft);
        return dataService.get(draftId, userId);
    }

    @Transactional
    public ExamPaperVO confirm(Long draftId, PrivateExamDraftConfirmRequest request, Long userId) {
        if (!Boolean.TRUE.equals(request.getConfirmed())) {
            throw validation("必须显式确认启用试卷");
        }
        PrivateExamImportDraft draft = dataService.ownedDraftForUpdate(draftId, userId);
        if ("CONFIRMED".equals(draft.getStatus()) && draft.getConfirmedPaperId() != null) {
            return importService.getConfirmedPaper(draft.getConfirmedPaperId(), userId);
        }
        if (!"READY".equals(draft.getStatus())) {
            throw validation("所有题目逐题复核后才能启用试卷");
        }
        List<PrivateExamDraftQuestion> questions = dataService.questions(draftId);
        if (questions.isEmpty()
                || questions.stream().anyMatch(item -> !"REVIEWED".equals(item.getReviewStatus()))) {
            throw validation("草稿复核状态不完整");
        }
        UserExamSource source = dataService.ownedSource(draft.getSourceRecordId(), userId);
        ExamPaperVO paper = importService.createConfirmedPaper(draft.getTitle(), draft.getCourseId(),
                draft.getDuration(), source, dataService.toConfirmedQuestions(questions), userId);
        draft.setStatus("CONFIRMED");
        draft.setConfirmedPaperId(paper.getId());
        dataService.updateDraft(draft);
        return paper;
    }

    private BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }
}
