package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamPaperVO;
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

    /** 智能组卷请求参数。 */
    public static class SmartExamRequest {
        private Long courseId;
        private Integer questionCount = 20;
        /** 难度分布偏好：EASY/BALANCED/HARD/ADAPTIVE，默认 ADAPTIVE */
        private String difficultyMode = "ADAPTIVE";
        /** 是否优先包含用户的错题 */
        private boolean includeWrongQuestions = true;
        /** 试卷标题（可选，为空则自动生成） */
        private String title;
        /** 考试时长（分钟） */
        private Integer duration = 60;

        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public Integer getQuestionCount() { return questionCount; }
        public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
        public String getDifficultyMode() { return difficultyMode; }
        public void setDifficultyMode(String difficultyMode) { this.difficultyMode = difficultyMode; }
        public boolean isIncludeWrongQuestions() { return includeWrongQuestions; }
        public void setIncludeWrongQuestions(boolean includeWrongQuestions) {
            this.includeWrongQuestions = includeWrongQuestions;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
    }

    /** 智能组卷结果预览（不含实际创建试卷）。 */
    public static class SmartExamPreview {
        private String title;
        private String description;
        private Long courseId;
        private String courseName;
        private Integer questionCount;
        private Integer totalScore;
        private Integer duration;
        /** 各知识点题目数分布 */
        private Map<String, Integer> knowledgePointDistribution;
        /** 各难度题目数分布 */
        private Map<String, Integer> difficultyDistribution;
        /** 选中的题目 ID 列表 */
        private List<Long> questionIds;
        /** 推荐理由 */
        private String recommendation;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public Integer getQuestionCount() { return questionCount; }
        public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
        public Integer getTotalScore() { return totalScore; }
        public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
        public Map<String, Integer> getKnowledgePointDistribution() { return knowledgePointDistribution; }
        public void setKnowledgePointDistribution(Map<String, Integer> knowledgePointDistribution) {
            this.knowledgePointDistribution = knowledgePointDistribution;
        }
        public Map<String, Integer> getDifficultyDistribution() { return difficultyDistribution; }
        public void setDifficultyDistribution(Map<String, Integer> difficultyDistribution) {
            this.difficultyDistribution = difficultyDistribution;
        }
        public List<Long> getQuestionIds() { return questionIds; }
        public void setQuestionIds(List<Long> questionIds) { this.questionIds = questionIds; }
        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    }
}
