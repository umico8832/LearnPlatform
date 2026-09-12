package com.learnplatform.service;

import com.learnplatform.dto.AiResponse;
import com.learnplatform.dto.ReviewContextVO;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/** 兼容既有 AI API 的薄门面，具体提示词与数据读取由领域协作者负责。 */
@Service
public class AiService {
    private final AiQuestionAssistanceService questionAssistanceService;
    private final AiReviewSuggestionService reviewSuggestionService;
    private final AiKnowledgeSummaryService knowledgeSummaryService;

    public AiService(
            AiQuestionAssistanceService questionAssistanceService,
            AiReviewSuggestionService reviewSuggestionService,
            AiKnowledgeSummaryService knowledgeSummaryService) {
        this.questionAssistanceService = questionAssistanceService;
        this.reviewSuggestionService = reviewSuggestionService;
        this.knowledgeSummaryService = knowledgeSummaryService;
    }

    public AiResponse generateExplanation(Long questionId) {
        return questionAssistanceService.generateExplanation(questionId);
    }

    public AiResponse generateExplanation(Long questionId, Long userId) {
        return questionAssistanceService.generateExplanation(questionId, userId);
    }

    public void generateExplanationStream(Long questionId, Consumer<String> onContent) {
        questionAssistanceService.generateExplanationStream(questionId, onContent);
    }

    public void generateExplanationStream(Long questionId, Long userId, Consumer<String> onContent) {
        questionAssistanceService.generateExplanationStream(questionId, userId, onContent);
    }

    public AiResponse generateVariant(Long questionId) {
        return questionAssistanceService.generateVariant(questionId);
    }

    public AiResponse generateVariant(Long questionId, Long userId) {
        return questionAssistanceService.generateVariant(questionId, userId);
    }

    public void generateVariantStream(Long questionId, Consumer<String> onContent) {
        questionAssistanceService.generateVariantStream(questionId, onContent);
    }

    public void generateVariantStream(Long questionId, Long userId, Consumer<String> onContent) {
        questionAssistanceService.generateVariantStream(questionId, userId, onContent);
    }

    public void generatePaperLearningAssistanceStream(Long questionId, String assistanceType,
                                                       String learningContext, Long userId,
                                                       Consumer<String> onContent) {
        questionAssistanceService.generatePaperLearningAssistanceStream(
                questionId, assistanceType, learningContext, userId, onContent);
    }

    public AiResponse generateReviewSuggestion(Long userId, Long courseId) {
        return reviewSuggestionService.generateReviewSuggestion(userId, courseId);
    }

    public void generateReviewSuggestionStream(Long userId, Long courseId, Consumer<String> onContent) {
        reviewSuggestionService.generateReviewSuggestionStream(userId, courseId, onContent);
    }

    public AiResponse generateSummary(Long knowledgePointId) {
        return knowledgeSummaryService.generateSummary(knowledgePointId, null);
    }

    public AiResponse generateSummary(Long knowledgePointId, Long userId) {
        return knowledgeSummaryService.generateSummary(knowledgePointId, userId);
    }

    public AiResponse generateReviewBasedSuggestion(Long userId) {
        return reviewSuggestionService.generateReviewBasedSuggestion(userId);
    }

    public void generateReviewBasedSuggestionStream(Long userId, Consumer<String> onContent) {
        reviewSuggestionService.generateReviewBasedSuggestionStream(userId, onContent);
    }

    public AiPrompt buildReviewSuggestionPromptWithContext(ReviewContextVO context) {
        return reviewSuggestionService.buildReviewSuggestionPromptWithContext(context);
    }

    public AiResponse generateReviewBasedSuggestionWithContext(Long userId, ReviewContextVO context) {
        return reviewSuggestionService.generateReviewBasedSuggestionWithContext(userId, context);
    }

    public void generateReviewBasedSuggestionStreamWithContext(
            Long userId, ReviewContextVO context, Consumer<String> onContent) {
        reviewSuggestionService.generateReviewBasedSuggestionStreamWithContext(userId, context, onContent);
    }

    record AiPrompt(String systemPrompt, String userPrompt) { }
}
