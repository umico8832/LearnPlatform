package com.learnplatform.service;

import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.dto.SimilarQuestionVO;
import com.learnplatform.entity.PracticeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * 学习诊断门面，编排数据加载、指标分析、推荐和 AI 建议能力。
 */
@Service
public class LearningDiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(LearningDiagnosisService.class);

    private final LearningDiagnosisDataLoader dataLoader;
    private final LearningDiagnosisKnowledgeAnalyzer knowledgeAnalyzer;
    private final LearningDiagnosisErrorPatternAnalyzer errorPatternAnalyzer;
    private final LearningDiagnosisHabitAnalyzer habitAnalyzer;
    private final LearningDiagnosisRecommendationService recommendationService;
    private final LearningDiagnosisAiAdviceService aiAdviceService;
    private final LearningQuestionErrorAnalysisService questionErrorAnalysisService;
    private final SimilarQuestionRecommendationService similarQuestionRecommendationService;

    public LearningDiagnosisService(
            LearningDiagnosisDataLoader dataLoader,
            LearningDiagnosisKnowledgeAnalyzer knowledgeAnalyzer,
            LearningDiagnosisErrorPatternAnalyzer errorPatternAnalyzer,
            LearningDiagnosisHabitAnalyzer habitAnalyzer,
            LearningDiagnosisRecommendationService recommendationService,
            LearningDiagnosisAiAdviceService aiAdviceService,
            LearningQuestionErrorAnalysisService questionErrorAnalysisService,
            SimilarQuestionRecommendationService similarQuestionRecommendationService) {
        this.dataLoader = dataLoader;
        this.knowledgeAnalyzer = knowledgeAnalyzer;
        this.errorPatternAnalyzer = errorPatternAnalyzer;
        this.habitAnalyzer = habitAnalyzer;
        this.recommendationService = recommendationService;
        this.aiAdviceService = aiAdviceService;
        this.questionErrorAnalysisService = questionErrorAnalysisService;
        this.similarQuestionRecommendationService = similarQuestionRecommendationService;
    }

    /** 获取完整学习诊断。 */
    @Cacheable(value = "learningDiagnosis", key = "#userId")
    public LearningDiagnosisVO getDiagnosis(Long userId) {
        log.info("生成学习诊断: userId={}", userId);
        LearningDiagnosisDataLoader.DiagnosisData data = dataLoader.load(userId);
        List<PracticeRecord> records = data.records();

        LearningDiagnosisVO diagnosis = new LearningDiagnosisVO();
        diagnosis.setTotalPractice(records.size());
        long correctCount = records.stream()
                .filter(record -> record.getIsCorrect() != null && record.getIsCorrect() == 1)
                .count();
        double overallRate = records.isEmpty()
                ? 0
                : Math.round(correctCount * 1000.0 / records.size()) / 10.0;
        diagnosis.setOverallCorrectRate(overallRate);
        diagnosis.setStreakDays(habitAnalyzer.calculateStreak(records));
        diagnosis.setActiveDaysLast30(habitAnalyzer.calculateActiveDays(records, 30));
        diagnosis.setWeakPoints(knowledgeAnalyzer.computeWeakPoints(
                data.knowledgePoints(), records, data.wrongs(), data.questionToKnowledgePoints()));
        diagnosis.setCourseMasteries(knowledgeAnalyzer.computeCourseMasteries(
                records, data.wrongs(), data.knowledgePoints(), data.questionToKnowledgePoints()));
        diagnosis.setErrorPatterns(errorPatternAnalyzer.compute(
                data.wrongs(), records, data.knowledgePoints(), data.questionToKnowledgePoints()));
        diagnosis.setLearningHabit(habitAnalyzer.computeLearningHabit(records));
        diagnosis.setDailyRecommendations(recommendationService.recommend(
                userId, records, data.wrongs(), data.knowledgePoints(), data.questionToKnowledgePoints()));
        diagnosis.setDailyAdvice(habitAnalyzer.generateDailyAdvice(diagnosis));
        return diagnosis;
    }

    /** 生成同步 AI 个性化学习建议。 */
    public String generateAiAdvice(Long userId) {
        return aiAdviceService.generate(userId, getDiagnosis(userId));
    }

    /** 生成流式 AI 个性化学习建议。 */
    public void generateAiAdviceStream(Long userId, Consumer<String> onContent) {
        aiAdviceService.generateStream(userId, getDiagnosis(userId), onContent);
    }

    /** 委托独立服务完成单题错因分析。 */
    public LearningDiagnosisVO.QuestionErrorAnalysis analyzeQuestionError(Long userId, Long questionId) {
        return questionErrorAnalysisService.analyzeQuestionError(userId, questionId);
    }

    /** 委托独立服务完成相似题推荐。 */
    public SimilarQuestionVO findSimilarQuestions(Long userId, Long questionId, int limit) {
        return similarQuestionRecommendationService.findSimilarQuestions(userId, questionId, limit);
    }
}
