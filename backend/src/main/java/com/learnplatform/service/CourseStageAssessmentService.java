package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseStageAssessmentMapper;
import com.learnplatform.mapper.CourseStageAssessmentQuestionMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
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
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final AnswerEvaluator answerEvaluator;
    private final ObjectMapper objectMapper;
    private final WrongQuestionService wrongQuestionService;
    private final SpacedRepetitionService repetitionService;
    private final CourseLearningEventService eventService;

    public CourseStageAssessmentService(
            CourseStageAssessmentMapper assessmentMapper,
            CourseStageAssessmentQuestionMapper assessmentQuestionMapper,
            QuestionMapper questionMapper,
            QuestionOptionMapper optionMapper,
            KnowledgePointMapper knowledgePointMapper,
            AnswerEvaluator answerEvaluator,
            ObjectMapper objectMapper,
            WrongQuestionService wrongQuestionService,
            SpacedRepetitionService repetitionService,
            CourseLearningEventService eventService) {
        this.assessmentMapper = assessmentMapper;
        this.assessmentQuestionMapper = assessmentQuestionMapper;
        this.questionMapper = questionMapper;
        this.optionMapper = optionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.answerEvaluator = answerEvaluator;
        this.objectMapper = objectMapper;
        this.wrongQuestionService = wrongQuestionService;
        this.repetitionService = repetitionService;
        this.eventService = eventService;
    }

    @Transactional
    public CourseStageAssessmentVO start(Long userId, Long courseId,
                                         CourseStageAssessmentCreateRequest request) {
        if (assessmentMapper.lockUserCourse(userId, courseId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "请先将课程加入个人课程库");
        }
        CourseStageAssessment active = assessmentMapper.selectActive(userId, courseId);
        if (active != null) { return toView(active); }

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
            createSnapshot(assessment.getId(), sortOrder++, question);
        }
        return toView(assessment);
    }

    @Transactional
    public CourseStageAssessmentVO submit(Long assessmentId, Long userId,
                                          CourseStageAssessmentSubmitRequest request) {
        CourseStageAssessment assessment = assessmentMapper.selectOwnedForUpdate(assessmentId, userId);
        if (assessment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "阶段测评不存在");
        }
        if (COMPLETED.equals(assessment.getStatus())) { return toView(assessment); }
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
            updateLearningFacts(userId, item, correct, answeredTime);
        }
        assessment.setStatus(COMPLETED);
        assessment.setCorrectCount(correctCount);
        assessment.setActiveSessionKey(null);
        assessment.setCompleteTime(answeredTime);
        assessmentMapper.complete(assessmentId, userId, correctCount, answeredTime);
        return toView(assessment);
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
                .map(item -> toSummary(item, sourcesByAssessment.getOrDefault(item.getId(), List.of())))
                .toList());
        return result;
    }

    public CourseStageAssessmentVO getCompleted(Long assessmentId, Long userId) {
        CourseStageAssessment assessment = assessmentMapper.selectOwned(assessmentId, userId);
        if (assessment == null || !COMPLETED.equals(assessment.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "已完成阶段测评不存在");
        }
        return toView(assessment);
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

    private CourseStageAssessmentSummaryVO toSummary(
            CourseStageAssessment assessment, List<CourseStageAssessmentQuestion> sourceItems) {
        CourseStageAssessmentSummaryVO view = new CourseStageAssessmentSummaryVO();
        view.setId(assessment.getId());
        view.setSelectionStrategy(assessment.getSelectionStrategy());
        view.setTargetKnowledgePointId(assessment.getTargetKnowledgePointId());
        view.setTargetKnowledgePointName(assessment.getTargetKnowledgePointNameSnapshot());
        view.setQuestionCount(assessment.getQuestionCount());
        view.setCorrectCount(assessment.getCorrectCount());
        view.setStartTime(assessment.getStartTime());
        view.setCompleteTime(assessment.getCompleteTime());
        view.setSourceComposition(CourseStageAssessmentSourceComposition.from(sourceItems));
        return view;
    }

    private void createSnapshot(Long assessmentId, int sortOrder, Question question) {
        List<QuestionOption> options = optionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, question.getId())
                .orderByAsc(QuestionOption::getSortOrder));
        List<QuestionOption> correctOptions = options.stream()
                .filter(option -> Integer.valueOf(1).equals(option.getIsCorrect())).toList();
        String correctAnswer = answerEvaluator.buildCorrectAnswer(correctOptions, question.getQuestionType());
        if (options.isEmpty() || correctAnswer.isBlank()) {
            throw validation("课程题目缺少可判分选项");
        }
        List<CourseStageAssessmentVO.OptionItem> optionSnapshot = options.stream()
                .map(option -> new CourseStageAssessmentVO.OptionItem(
                        "TRUE_FALSE".equals(question.getQuestionType())
                                ? trueFalseValue(option.getContent()) : option.getOptionLabel(),
                        option.getContent()))
                .toList();
        CourseStageAssessmentQuestion item = new CourseStageAssessmentQuestion();
        item.setAssessmentId(assessmentId);
        item.setQuestionId(question.getId());
        item.setSortOrder(sortOrder);
        item.setQuestionType(question.getQuestionType());
        item.setSourceTypeSnapshot(question.getSourceType() == null ? "MANUAL" : question.getSourceType());
        item.setSourceCategorySnapshot(sourceCategory(question));
        item.setOriginQuestionIdSnapshot(question.getOriginQuestionId());
        item.setKnowledgePointsJson(snapshotKnowledgePoints(question.getId()));
        item.setContentSnapshot(question.getContent());
        item.setOptionsSnapshot(writeJson(optionSnapshot));
        item.setCorrectAnswerSnapshot(correctAnswer);
        item.setAnalysisSnapshot(question.getAnalysis());
        item.setScore(question.getScore() == null ? 1 : question.getScore());
        assessmentQuestionMapper.insert(item);
    }

    private void updateLearningFacts(Long userId, CourseStageAssessmentQuestion item,
                                     boolean correct, LocalDateTime answeredTime) {
        Question question = questionMapper.selectById(item.getQuestionId());
        if (question == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "测评原题已不存在");
        }
        if (correct) { wrongQuestionService.removeOnCorrect(userId, question.getId()); }
        else { wrongQuestionService.addWrongQuestion(userId, question.getId(), item.getUserAnswer()); }
        repetitionService.addToReviewPlan(userId, question.getId());
        eventService.recordQuestionAnswer(userId, question, "STAGE_ASSESSMENT_ANSWERED",
                "STAGE_ASSESSMENT", item.getId(), correct, answeredTime);
    }

    private CourseStageAssessmentVO toView(CourseStageAssessment assessment) {
        boolean completed = COMPLETED.equals(assessment.getStatus());
        CourseStageAssessmentVO view = new CourseStageAssessmentVO();
        view.setId(assessment.getId());
        view.setCourseId(assessment.getCourseId());
        view.setStatus(assessment.getStatus());
        view.setSelectionStrategy(assessment.getSelectionStrategy());
        view.setTargetKnowledgePointId(assessment.getTargetKnowledgePointId());
        view.setTargetKnowledgePointName(assessment.getTargetKnowledgePointNameSnapshot());
        view.setQuestionCount(assessment.getQuestionCount());
        view.setCorrectCount(assessment.getCorrectCount());
        view.setStartTime(assessment.getStartTime());
        view.setCompleteTime(assessment.getCompleteTime());
        List<CourseStageAssessmentQuestion> items = assessmentQuestionMapper.selectByAssessmentId(assessment.getId());
        view.setSourceComposition(CourseStageAssessmentSourceComposition.from(items));
        view.setKnowledgePointSummary(completed
                ? CourseStageAssessmentKnowledgePointSummary.from(items, objectMapper) : null);
        view.setQuestions(items.stream().map(item -> toQuestionView(item, completed)).toList());
        return view;
    }

    private CourseStageAssessmentVO.QuestionItem toQuestionView(
            CourseStageAssessmentQuestion item, boolean completed) {
        CourseStageAssessmentVO.QuestionItem view = new CourseStageAssessmentVO.QuestionItem();
        view.setId(item.getId());
        view.setQuestionId(item.getQuestionId());
        view.setSortOrder(item.getSortOrder());
        view.setQuestionType(item.getQuestionType());
        view.setSourceType(item.getSourceTypeSnapshot());
        view.setSourceCategory(item.getSourceCategorySnapshot());
        view.setOriginQuestionId(item.getOriginQuestionIdSnapshot());
        view.setContent(item.getContentSnapshot());
        view.setOptions(readOptions(item.getOptionsSnapshot()));
        view.setScore(item.getScore());
        view.setUserAnswer(item.getUserAnswer());
        view.setCorrect(item.getIsCorrect() == null ? null : item.getIsCorrect() == 1);
        view.setCorrectAnswer(completed ? item.getCorrectAnswerSnapshot() : null);
        view.setAnalysis(completed ? item.getAnalysisSnapshot() : null);
        view.setKnowledgePoints(readKnowledgePoints(item.getKnowledgePointsJson()));
        return view;
    }

    private String snapshotKnowledgePoints(Long questionId) {
        List<KnowledgePoint> points = knowledgePointMapper.selectByQuestionId(questionId);
        if (points.isEmpty()) { return null; }
        return writeJson(points.stream()
                .map(point -> new CourseStageAssessmentVO.KnowledgePointVO(point.getId(), point.getName()))
                .toList());
    }

    private List<CourseStageAssessmentVO.KnowledgePointVO> readKnowledgePoints(String json) {
        return CourseStageAssessmentKnowledgePointSummary.readKnowledgePoints(json, objectMapper);
    }

    String sourceCategory(Question question) {
        if ("AI_GENERATED".equals(question.getSourceType())) { return "AI_GENERATED"; }
        if ("PRIVATE".equals(question.getVisibility())
                || "USER_PRIVATE_IMPORT".equals(question.getSourceType())) {
            return "USER_PRIVATE";
        }
        Long officialReferences = assessmentMapper.countVerifiedOfficialPaperReferences(question.getId());
        return officialReferences != null && officialReferences > 0 ? "OFFICIAL_EXAM" : "MANUAL";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "测评题目快照保存失败");
        }
    }

    private List<CourseStageAssessmentVO.OptionItem> readOptions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "测评题目快照损坏");
        }
    }

    private String trueFalseValue(String content) {
        return "TRUE".equalsIgnoreCase(content) || "正确".equals(content) ? "TRUE" : "FALSE";
    }

    private BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }
}
