package com.learnplatform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.CourseStageAssessmentCreateRequest;
import com.learnplatform.dto.CourseStageAssessmentSubmitRequest;
import com.learnplatform.dto.CourseStageAssessmentSummaryVO;
import com.learnplatform.dto.CourseStageAssessmentVO;
import com.learnplatform.entity.CourseStageAssessment;
import com.learnplatform.entity.CourseStageAssessmentQuestion;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.CourseStageAssessmentMapper;
import com.learnplatform.mapper.CourseStageAssessmentQuestionMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CourseStageAssessmentService {
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";

    private final CourseStageAssessmentMapper assessmentMapper;
    private final CourseStageAssessmentQuestionMapper assessmentQuestionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final AnswerEvaluator answerEvaluator;
    private final CourseStageAssessmentSnapshotService snapshotService;
    private final CourseStageAssessmentLearningFactService learningFactService;
    private final CourseStageAssessmentViewService viewService;

    public CourseStageAssessmentService(
            CourseStageAssessmentMapper assessmentMapper,
            CourseStageAssessmentQuestionMapper assessmentQuestionMapper,
            KnowledgePointMapper knowledgePointMapper,
            AnswerEvaluator answerEvaluator,
            CourseStageAssessmentSnapshotService snapshotService,
            CourseStageAssessmentLearningFactService learningFactService,
            CourseStageAssessmentViewService viewService) {
        this.assessmentMapper = assessmentMapper;
        this.assessmentQuestionMapper = assessmentQuestionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.answerEvaluator = answerEvaluator;
        this.snapshotService = snapshotService;
        this.learningFactService = learningFactService;
        this.viewService = viewService;
    }

    @Transactional
    public CourseStageAssessmentVO start(Long userId, Long courseId,
                                         CourseStageAssessmentCreateRequest request) {
        if (assessmentMapper.lockUserCourse(userId, courseId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "请先将课程加入个人课程库");
        }
        CourseStageAssessment active = assessmentMapper.selectActive(userId, courseId);
        if (active != null) { return viewService.toView(active); }

        int requestedCount = request == null || request.getQuestionCount() == null ? 5 : request.getQuestionCount();
        if (requestedCount < 1 || requestedCount > 20) {
            throw validation("测评题数必须在1到20之间");
        }
        KnowledgePoint target = reviewedCourseKnowledgePoint(courseId,
                request == null ? null : request.getKnowledgePointId());
        Long targetId = target == null ? null : target.getId();
        List<Question> questions = assessmentMapper.selectCandidateQuestions(userId, courseId,
                targetId, requestedCount);
        if (questions.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, target == null
                    ? "课程暂无可用于阶段测评的客观题"
                    : "该知识点暂无可用于阶段测评的客观题");
        }

        CourseStageAssessment assessment = new CourseStageAssessment();
        assessment.setUserId(userId);
        assessment.setCourseId(courseId);
        assessment.setStatus(IN_PROGRESS);
        assessment.setSelectionStrategy(assessmentMapper.countPrioritySignals(userId, courseId, targetId) > 0
                ? "LEARNING_STATE_PRIORITY" : "COURSE_SEQUENCE_FALLBACK");
        if (target != null) {
            assessment.setTargetKnowledgePointId(target.getId());
            assessment.setTargetKnowledgePointNameSnapshot(target.getName());
        }
        assessment.setQuestionCount(questions.size());
        assessment.setActiveSessionKey("ACTIVE");
        assessment.setStartTime(LocalDateTime.now());
        assessmentMapper.insert(assessment);

        int sortOrder = 1;
        for (Question question : questions) {
            snapshotService.createSnapshot(assessment.getId(), sortOrder++, question);
        }
        return viewService.toView(assessment);
    }

    @Transactional
    public CourseStageAssessmentVO submit(Long assessmentId, Long userId,
                                          CourseStageAssessmentSubmitRequest request) {
        CourseStageAssessment assessment = assessmentMapper.selectOwnedForUpdate(assessmentId, userId);
        if (assessment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "阶段测评不存在");
        }
        if (COMPLETED.equals(assessment.getStatus())) { return viewService.toView(assessment); }
        List<CourseStageAssessmentQuestion> items = assessmentQuestionMapper.selectByAssessmentId(assessmentId);
        if (request == null || request.getAnswers() == null || request.getAnswers().size() != items.size()) {
            throw validation("必须提交完整测评答案");
        }
        Map<Long, String> answers = new HashMap<>();
        for (CourseStageAssessmentSubmitRequest.Answer answer : request.getAnswers()) {
            if (answer == null || answer.getAssessmentQuestionId() == null
                    || answer.getUserAnswer() == null || answer.getUserAnswer().isBlank()
                    || answers.put(answer.getAssessmentQuestionId(), answer.getUserAnswer().trim()) != null) {
                throw validation("必须提交完整测评答案");
            }
        }
        if (items.stream().anyMatch(item -> !answers.containsKey(item.getId()))) {
            throw validation("必须提交完整测评答案");
        }

        int correctCount = 0;
        LocalDateTime answeredTime = LocalDateTime.now();
        for (CourseStageAssessmentQuestion item : items) {
            String userAnswer = answers.get(item.getId());
            boolean correct = answerEvaluator.isCorrect(
                    item.getQuestionType(), userAnswer, item.getCorrectAnswerSnapshot());
            if (correct) { correctCount++; }
            item.setUserAnswer(userAnswer);
            item.setIsCorrect(correct ? 1 : 0);
            item.setAnsweredTime(answeredTime);
            assessmentQuestionMapper.updateById(item);
            learningFactService.record(userId, item, correct, answeredTime);
        }
        assessment.setStatus(COMPLETED);
        assessment.setCorrectCount(correctCount);
        assessment.setActiveSessionKey(null);
        assessment.setCompleteTime(answeredTime);
        assessmentMapper.complete(assessmentId, userId, correctCount, answeredTime);
        return viewService.toView(assessment);
    }

    public Page<CourseStageAssessmentSummaryVO> listCompleted(
            Long userId, Long courseId, int pageNum, int pageSize) {
        return listCompleted(userId, courseId, pageNum, pageSize, null);
    }

    /** 分页查询本人已完成测评；可按知识点范围过滤（基于逐题快照的知识点归属）。 */
    public Page<CourseStageAssessmentSummaryVO> listCompleted(
            Long userId, Long courseId, int pageNum, int pageSize, Long knowledgePointId) {
        requireCourseInLibrary(userId, courseId);
        if (pageNum < 1 || pageSize < 1 || pageSize > 50) {
            throw validation("分页参数不合法");
        }
        Page<CourseStageAssessment> source = assessmentMapper.selectCompletedPage(
                new Page<>(pageNum, pageSize), userId, courseId, knowledgePointId);
        Page<CourseStageAssessmentSummaryVO> result = new Page<>(source.getCurrent(), source.getSize(),
                source.getTotal());
        List<Long> assessmentIds = source.getRecords().stream().map(CourseStageAssessment::getId).toList();
        Map<Long, List<CourseStageAssessmentQuestion>> sourcesByAssessment = assessmentIds.isEmpty()
                ? Map.of()
                : assessmentQuestionMapper.selectSourcesByAssessmentIds(assessmentIds).stream()
                        .collect(Collectors.groupingBy(CourseStageAssessmentQuestion::getAssessmentId));
        result.setRecords(source.getRecords().stream()
                .map(item -> viewService.toSummary(item,
                        sourcesByAssessment.getOrDefault(item.getId(), List.of())))
                .toList());
        return result;
    }

    public CourseStageAssessmentVO getCompleted(Long assessmentId, Long userId) {
        CourseStageAssessment assessment = assessmentMapper.selectOwned(assessmentId, userId);
        if (assessment == null || !COMPLETED.equals(assessment.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "已完成阶段测评不存在");
        }
        return viewService.toView(assessment);
    }

    private void requireCourseInLibrary(Long userId, Long courseId) {
        Long count = assessmentMapper.countUserCourse(userId, courseId);
        if (count == null || count == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "请先将课程加入个人课程库");
        }
    }

    private KnowledgePoint reviewedCourseKnowledgePoint(Long courseId, Long knowledgePointId) {
        if (knowledgePointId == null) { return null; }
        KnowledgePoint target = knowledgePointMapper.selectById(knowledgePointId);
        if (target == null || !courseId.equals(target.getCourseId())
                || !"REVIEWED".equals(target.getContentReviewStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识点不属于当前课程或尚未通过内容审查");
        }
        return target;
    }

    String sourceCategory(Question question) {
        return snapshotService.sourceCategory(question);
    }

    private BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }
}
