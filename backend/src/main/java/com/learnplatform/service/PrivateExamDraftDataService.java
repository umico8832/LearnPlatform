package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.PrivateExamDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDraftVO;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.entity.PrivateExamDraftQuestion;
import com.learnplatform.entity.PrivateExamImportDraft;
import com.learnplatform.entity.UserExamSource;
import com.learnplatform.mapper.PrivateExamDraftQuestionMapper;
import com.learnplatform.mapper.PrivateExamImportDraftMapper;
import com.learnplatform.mapper.UserExamSourceMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PrivateExamDraftDataService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<List<PrivateExamDraftVO.OptionItem>> OPTION_LIST = new TypeReference<>() { };

    private final UserExamSourceMapper sourceMapper;
    private final PrivateExamSourceStorageService sourceStorageService;
    private final PrivateExamImportDraftMapper draftMapper;
    private final PrivateExamDraftQuestionMapper questionMapper;
    private final ObjectMapper objectMapper;

    public PrivateExamDraftDataService(UserExamSourceMapper sourceMapper,
                                       PrivateExamSourceStorageService sourceStorageService,
                                       PrivateExamImportDraftMapper draftMapper,
                                       PrivateExamDraftQuestionMapper questionMapper,
                                       ObjectMapper objectMapper) {
        this.sourceMapper = sourceMapper;
        this.sourceStorageService = sourceStorageService;
        this.draftMapper = draftMapper;
        this.questionMapper = questionMapper;
        this.objectMapper = objectMapper;
    }

    public PrivateExamDraftVO create(PrivateExamDraftCreateRequest request, Long userId,
                                     PrivateExamImportPreviewVO preview, byte[] sourceFile,
                                     String sourceMediaType) {
        UserExamSource source = new UserExamSource();
        source.setOwnerUserId(userId);
        source.setSourceName(request.getSourceName().trim());
        source.setSourceFormat(request.getSourceFormat());
        source.setContentSha256(preview.getContentHash());
        source.setOriginalContent(request.getContent());
        sourceStorageService.attachFileWithinQuota(source, userId, sourceFile, sourceMediaType);
        sourceMapper.insert(source);

        PrivateExamImportDraft draft = new PrivateExamImportDraft();
        draft.setOwnerUserId(userId);
        draft.setTitle(request.getTitle().trim());
        draft.setCourseId(request.getCourseId());
        draft.setDuration(request.getDuration() != null ? request.getDuration() : 60);
        draft.setSourceRecordId(source.getId());
        draft.setStatus("DRAFT");
        draftMapper.insert(draft);

        for (int index = 0; index < preview.getQuestions().size(); index++) {
            PrivateExamImportPreviewVO.QuestionPreview item = preview.getQuestions().get(index);
            List<String> originalAnswers = answerLabels(item);
            PrivateExamDraftQuestion question = new PrivateExamDraftQuestion();
            question.setDraftId(draft.getId());
            question.setSortOrder(index + 1);
            question.setContent(item.getContent());
            question.setQuestionType(item.getQuestionType());
            question.setScore(item.getScore());
            question.setOptionsJson(writeJson(item.getOptions().stream().map(option ->
                    new PrivateExamDraftVO.OptionItem(option.getLabel(), option.getContent())).toList()));
            question.setOriginalAnswerJson(originalAnswers.isEmpty() ? null : writeJson(originalAnswers));
            question.setOriginalAnalysis(item.getAnalysis());
            question.setGenerationStatus(originalAnswers.isEmpty() ? "PENDING" : "NOT_REQUIRED");
            question.setReviewStatus("PENDING");
            questionMapper.insert(question);
        }
        return get(draft.getId(), userId);
    }

    public PrivateExamDraftVO get(Long draftId, Long userId) {
        PrivateExamImportDraft draft = ownedDraft(draftId, userId);
        return toView(draft, questions(draftId));
    }

    public List<PrivateExamDraftVO> listActive(Long userId) {
        return draftMapper.selectList(new LambdaQueryWrapper<PrivateExamImportDraft>()
                        .eq(PrivateExamImportDraft::getOwnerUserId, userId)
                        .ne(PrivateExamImportDraft::getStatus, "CONFIRMED")
                        .orderByDesc(PrivateExamImportDraft::getUpdateTime))
                .stream().map(draft -> toView(draft, questions(draft.getId()))).toList();
    }

    public PrivateExamImportDraft ownedDraft(Long draftId, Long userId) {
        PrivateExamImportDraft draft = draftMapper.selectById(draftId);
        if (draft == null || !userId.equals(draft.getOwnerUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "私有试卷草稿不存在");
        }
        return draft;
    }

    public PrivateExamImportDraft mutableDraft(Long draftId, Long userId) {
        PrivateExamImportDraft draft = ownedDraft(draftId, userId);
        if ("CONFIRMED".equals(draft.getStatus())) {
            throw validation("草稿已确认启用，不能继续修改");
        }
        return draft;
    }

    public PrivateExamImportDraft ownedDraftForUpdate(Long draftId, Long userId) {
        PrivateExamImportDraft draft = draftMapper.selectOwnedForUpdate(draftId, userId);
        if (draft == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "私有试卷草稿不存在");
        }
        return draft;
    }

    public PrivateExamDraftQuestion ownedQuestion(Long draftId, Long questionId) {
        PrivateExamDraftQuestion question = questionMapper.selectById(questionId);
        if (question == null || !draftId.equals(question.getDraftId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "草稿题目不存在");
        }
        return question;
    }

    public List<PrivateExamDraftQuestion> questions(Long draftId) {
        return questionMapper.selectList(new LambdaQueryWrapper<PrivateExamDraftQuestion>()
                .eq(PrivateExamDraftQuestion::getDraftId, draftId)
                .orderByAsc(PrivateExamDraftQuestion::getSortOrder));
    }

    public UserExamSource ownedSource(Long sourceId, Long userId) {
        UserExamSource source = sourceMapper.selectById(sourceId);
        if (source == null || !userId.equals(source.getOwnerUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "原始资料不存在");
        }
        return source;
    }

    public void updateQuestion(PrivateExamDraftQuestion question) {
        questionMapper.updateById(question);
    }

    public void updateDraft(PrivateExamImportDraft draft) {
        draftMapper.updateById(draft);
    }

    public void refreshGenerationStatus(PrivateExamImportDraft draft) {
        boolean generated = questions(draft.getId()).stream()
                .allMatch(question -> !"PENDING".equals(question.getGenerationStatus()));
        draft.setStatus(generated ? "AI_GENERATED" : "DRAFT");
        updateDraft(draft);
    }

    public List<String> normalizeAndValidateAnswers(List<String> labels, PrivateExamDraftQuestion question) {
        Set<String> allowed = new LinkedHashSet<>();
        options(question).forEach(option -> allowed.add(option.getLabel().toUpperCase(Locale.ROOT)));
        Set<String> normalized = new LinkedHashSet<>();
        for (String label : labels) {
            String value = label.trim().toUpperCase(Locale.ROOT);
            if (!allowed.contains(value)) { throw validation("答案必须匹配现有选项"); }
            normalized.add(value);
        }
        if (normalized.isEmpty()) { throw validation("答案不能为空"); }
        if (("SINGLE_CHOICE".equals(question.getQuestionType())
                || "TRUE_FALSE".equals(question.getQuestionType())) && normalized.size() != 1) {
            throw validation("单选或判断题只能选择一个答案");
        }
        if ("MULTIPLE_CHOICE".equals(question.getQuestionType()) && normalized.size() < 2) {
            throw validation("多选题至少选择两个答案");
        }
        return allowed.stream().filter(normalized::contains).toList();
    }

    public List<PrivateExamDraftVO.OptionItem> options(PrivateExamDraftQuestion question) {
        try {
            return objectMapper.readValue(question.getOptionsJson(), OPTION_LIST);
        } catch (Exception exception) {
            throw new IllegalStateException("私有试卷草稿选项格式无效", exception);
        }
    }

    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("私有试卷草稿序列化失败", exception);
        }
    }

    public List<PrivateExamImportPreviewVO.QuestionPreview> toConfirmedQuestions(
            List<PrivateExamDraftQuestion> questions) {
        return questions.stream().map(this::toConfirmedQuestion).toList();
    }

    private PrivateExamImportPreviewVO.QuestionPreview toConfirmedQuestion(PrivateExamDraftQuestion question) {
        List<String> answers = readStrings(question.getFinalAnswerJson());
        PrivateExamImportPreviewVO.QuestionPreview item = new PrivateExamImportPreviewVO.QuestionPreview();
        item.setContent(question.getContent());
        item.setQuestionType(question.getQuestionType());
        item.setScore(question.getScore());
        item.setAnswer(String.join(",", answers));
        item.setAnalysis(question.getFinalAnalysis());
        item.setAnswerComplete(true);
        item.setOptions(options(question).stream().map(option -> new PrivateExamImportPreviewVO.OptionPreview(
                option.getLabel(), option.getContent(), answers.contains(option.getLabel()))).toList());
        return item;
    }

    private PrivateExamDraftVO toView(PrivateExamImportDraft draft,
                                      List<PrivateExamDraftQuestion> questions) {
        PrivateExamDraftVO view = new PrivateExamDraftVO();
        view.setId(draft.getId());
        view.setTitle(draft.getTitle());
        view.setCourseId(draft.getCourseId());
        view.setDuration(draft.getDuration());
        view.setStatus(draft.getStatus());
        view.setConfirmedPaperId(draft.getConfirmedPaperId());
        UserExamSource source = sourceMapper.selectById(draft.getSourceRecordId());
        if (source != null && draft.getOwnerUserId().equals(source.getOwnerUserId())) {
            view.setSourceName(source.getSourceName());
            view.setSourceFormat(source.getSourceFormat());
            view.setOriginalFileAvailable(source.getSourceSize() != null && source.getSourceSize() > 0);
        } else {
            view.setOriginalFileAvailable(false);
        }
        view.setQuestionCount(questions.size());
        view.setReviewedQuestionCount((int) questions.stream()
                .filter(question -> "REVIEWED".equals(question.getReviewStatus())).count());
        view.setCreateTime(draft.getCreateTime());
        view.setQuestions(questions.stream().map(this::toQuestionView).toList());
        return view;
    }

    private PrivateExamDraftVO.DraftQuestion toQuestionView(PrivateExamDraftQuestion question) {
        PrivateExamDraftVO.DraftQuestion view = new PrivateExamDraftVO.DraftQuestion();
        view.setId(question.getId());
        view.setSortOrder(question.getSortOrder());
        view.setContent(question.getContent());
        view.setQuestionType(question.getQuestionType());
        view.setScore(question.getScore());
        view.setOptions(options(question));
        view.setOriginalAnswerLabels(readStrings(question.getOriginalAnswerJson()));
        view.setOriginalAnalysis(question.getOriginalAnalysis());
        view.setAiAnswerLabels(readStrings(question.getAiAnswerJson()));
        view.setAiAnalysis(question.getAiAnalysis());
        view.setGenerationStatus(question.getGenerationStatus());
        view.setFinalAnswerLabels(readStrings(question.getFinalAnswerJson()));
        view.setFinalAnalysis(question.getFinalAnalysis());
        view.setReviewStatus(question.getReviewStatus());
        return view;
    }

    private List<String> readStrings(String json) {
        if (json == null || json.isBlank()) { return List.of(); }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception exception) {
            throw new IllegalStateException("私有试卷草稿答案格式无效", exception);
        }
    }

    private List<String> answerLabels(PrivateExamImportPreviewVO.QuestionPreview question) {
        return question.getOptions().stream().filter(option -> Boolean.TRUE.equals(option.getCorrect()))
                .map(PrivateExamImportPreviewVO.OptionPreview::getLabel).toList();
    }

    private BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }
}
