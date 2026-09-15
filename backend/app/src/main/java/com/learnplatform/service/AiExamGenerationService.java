package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.exam.SmartExamPreview;
import com.learnplatform.dto.exam.SmartExamRequest;
import com.learnplatform.entity.Question;
import com.learnplatform.service.exam.AiExamCandidateLoader;
import com.learnplatform.service.exam.AiExamPaperCreationService;
import com.learnplatform.service.exam.AiExamPreviewPresentationService;
import com.learnplatform.service.exam.AiExamQuestionSelectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 根据课程知识点覆盖、难度分布和用户薄弱环节自动选择题目生成试卷。 */
@Service
public class AiExamGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AiExamGenerationService.class);

    private final AiExamCandidateLoader candidateLoader;
    private final AiExamQuestionSelectionService questionSelectionService;
    private final AiExamPreviewPresentationService previewPresentationService;
    private final AiExamPaperCreationService paperCreationService;

    public AiExamGenerationService(AiExamCandidateLoader candidateLoader,
                                   AiExamQuestionSelectionService questionSelectionService,
                                   AiExamPreviewPresentationService previewPresentationService,
                                   AiExamPaperCreationService paperCreationService) {
        this.candidateLoader = candidateLoader;
        this.questionSelectionService = questionSelectionService;
        this.previewPresentationService = previewPresentationService;
        this.paperCreationService = paperCreationService;
    }

    /** 智能组卷预览：分析题库并推荐题目组合。 */
    public SmartExamPreview preview(SmartExamRequest request, Long userId) {
        int questionCount = request.getQuestionCount() != null ? request.getQuestionCount() : 20;
        if (questionCount <= 0 || questionCount > 100) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "题目数量应在 1-100 之间");
        }

        List<Question> availableQuestions = candidateLoader.loadAvailableQuestions(request.getCourseId());
        Map<Long, List<Long>> questionKnowledgePoints = candidateLoader.loadQuestionKnowledgePoints(availableQuestions);
        Map<Long, String> knowledgePointNames = candidateLoader.loadKnowledgePointNames();
        Set<Long> wrongQuestionIds = userId != null && request.isIncludeWrongQuestions()
                ? candidateLoader.loadUserWrongQuestionIds(userId) : Collections.emptySet();
        Map<Integer, Double> difficultyAccuracy = userId != null
                && "ADAPTIVE".equals(request.getDifficultyMode())
                ? candidateLoader.loadUserDifficultyAccuracy(userId) : Collections.emptyMap();

        List<Long> selectedIds = questionSelectionService.select(availableQuestions, questionKnowledgePoints,
                wrongQuestionIds, difficultyAccuracy, request, questionCount);
        SmartExamPreview preview = previewPresentationService.create(
                request, availableQuestions, selectedIds, questionKnowledgePoints, knowledgePointNames,
                wrongQuestionIds, difficultyAccuracy);

        log.info("智能组卷预览: userId={}, courseId={}, questionCount={}, selectedCount={}",
                userId, request.getCourseId(), questionCount, selectedIds.size());
        return preview;
    }

    /** 确认创建智能试卷。 */
    public ExamPaperVO createSmartExam(SmartExamPreview preview, Long adminUserId) {
        ExamPaperVO paper = paperCreationService.create(preview, adminUserId);
        log.info("智能组卷创建成功: paperId={}, title={}, questionCount={}",
                paper.getId(), paper.getTitle(), paper.getQuestionCount());
        return paper;
    }

}
