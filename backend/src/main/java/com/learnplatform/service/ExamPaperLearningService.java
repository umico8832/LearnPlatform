package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamLearningAnswerRequest;
import com.learnplatform.dto.ExamLearningAnswerResultVO;
import com.learnplatform.dto.ExamLearningSessionVO;
import com.learnplatform.dto.QuestionOptionVO;
import com.learnplatform.entity.ExamLearningAnswer;
import com.learnplatform.entity.ExamLearningSession;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.ExamLearningAnswerMapper;
import com.learnplatform.mapper.ExamLearningSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExamPaperLearningService {

    private static final Logger log = LoggerFactory.getLogger(ExamPaperLearningService.class);
    private static final int ACTIVE = 0;
    private static final int COMPLETED = 1;

    private final ExamLearningSessionMapper sessionMapper;
    private final ExamLearningAnswerMapper learningAnswerMapper;
    private final ExamPaperLearningContextService contextService;
    private final AnswerEvaluator answerEvaluator;
    private final WrongQuestionService wrongQuestionService;
    private final SpacedRepetitionService spacedRepetitionService;
    private final CacheEvictService cacheEvictService;
    private final CourseLearningEventService courseLearningEventService;

    public ExamPaperLearningService(ExamLearningSessionMapper sessionMapper,
                                    ExamLearningAnswerMapper learningAnswerMapper,
                                    ExamPaperLearningContextService contextService,
                                    AnswerEvaluator answerEvaluator,
                                    WrongQuestionService wrongQuestionService,
                                    SpacedRepetitionService spacedRepetitionService,
                                    CacheEvictService cacheEvictService,
                                    CourseLearningEventService courseLearningEventService) {
        this.sessionMapper = sessionMapper;
        this.learningAnswerMapper = learningAnswerMapper;
        this.contextService = contextService;
        this.answerEvaluator = answerEvaluator;
        this.wrongQuestionService = wrongQuestionService;
        this.spacedRepetitionService = spacedRepetitionService;
        this.cacheEvictService = cacheEvictService;
        this.courseLearningEventService = courseLearningEventService;
    }

    @Transactional
    public ExamLearningSessionVO startSession(Long paperId, Long userId) {
        ExamPaper paper = contextService.loadEligiblePaper(paperId, userId);
        List<ExamQuestion> paperQuestions = contextService.loadPaperQuestions(paper);
        String activeKey = activeKey(userId, paperId);
        ExamLearningSession existing = findActiveSession(activeKey);
        if (existing != null) {
            return getSession(existing.getId(), userId);
        }

        ExamLearningSession session = new ExamLearningSession();
        session.setUserId(userId);
        session.setExamPaperId(paperId);
        session.setStatus(ACTIVE);
        session.setCurrentQuestionId(paperQuestions.get(0).getQuestionId());
        session.setActiveSessionKey(activeKey);
        session.setStartTime(LocalDateTime.now());
        try {
            sessionMapper.insert(session);
        } catch (DuplicateKeyException exception) {
            ExamLearningSession concurrent = findActiveSession(activeKey);
            if (concurrent == null) {
                throw exception;
            }
            return getSession(concurrent.getId(), userId);
        }
        return getSession(session.getId(), userId);
    }

    public ExamLearningSessionVO getSession(Long sessionId, Long userId) {
        ExamLearningSession session = loadOwnedSession(sessionId, userId, false);
        ExamPaper paper = contextService.loadEligiblePaper(session.getExamPaperId(), userId);
        List<ExamQuestion> paperQuestions = contextService.loadPaperQuestions(paper);
        List<ExamLearningAnswer> answers = learningAnswerMapper.selectList(
                new LambdaQueryWrapper<ExamLearningAnswer>()
                        .eq(ExamLearningAnswer::getSessionId, sessionId)
                        .orderByAsc(ExamLearningAnswer::getCreateTime));
        return toSessionVO(session, paper, paperQuestions, answers);
    }

    @Transactional
    public ExamLearningAnswerResultVO submitAnswer(Long sessionId, ExamLearningAnswerRequest request, Long userId) {
        if (request == null || request.getQuestionId() == null) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "题目ID不能为空");
        }
        if (request.getUserAnswer() == null || request.getUserAnswer().isBlank()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "答案不能为空");
        }
        ExamLearningSession session = loadOwnedSession(sessionId, userId, true);
        ensureActive(session);
        ExamPaper paper = contextService.loadEligiblePaper(session.getExamPaperId(), userId);
        List<ExamQuestion> paperQuestions = contextService.loadPaperQuestions(paper);
        ExamQuestion paperQuestion = paperQuestions.stream()
                .filter(item -> request.getQuestionId().equals(item.getQuestionId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.VALIDATION_ERROR,
                        "提交内容包含非本试卷题目"));

        Question question = contextService.loadPaperQuestion(paper, paperQuestion);
        List<QuestionOption> options = contextService.loadOptions(question.getId());
        String correctAnswer = buildCorrectAnswer(question, options);
        String userAnswer = request.getUserAnswer().trim();
        boolean manualSelfReview = "SHORT_ANSWER".equals(question.getQuestionType());
        boolean correct = !manualSelfReview
                && answerEvaluator.isCorrect(question.getQuestionType(), userAnswer, correctAnswer);
        long previousAttempts = learningAnswerMapper.selectCount(
                new LambdaQueryWrapper<ExamLearningAnswer>()
                        .eq(ExamLearningAnswer::getSessionId, sessionId)
                        .eq(ExamLearningAnswer::getQuestionId, question.getId()));

        ExamLearningAnswer answer = new ExamLearningAnswer();
        answer.setSessionId(sessionId);
        answer.setQuestionId(question.getId());
        answer.setAttemptNo(Math.toIntExact(previousAttempts + 1));
        answer.setUserAnswer(userAnswer);
        answer.setIsCorrect(manualSelfReview ? null : (correct ? 1 : 0));
        answer.setScore(manualSelfReview ? null : (correct ? scoreOf(paperQuestion) : 0));
        answer.setAnswerTime(request.getAnswerTime());
        answer.setCreateTime(LocalDateTime.now());
        learningAnswerMapper.insert(answer);

        session.setCurrentQuestionId(nextQuestionId(paperQuestions, question.getId()));
        sessionMapper.updateById(session);
        if (!manualSelfReview) {
            courseLearningEventService.recordQuestionAnswer(userId, question,
                    "PAPER_LEARNING_ANSWERED", "PAPER_LEARNING", answer.getId(), correct, answer.getCreateTime());
            updateWrongQuestionAndReview(userId, question.getId(), userAnswer, correct);
        }
        cacheEvictService.evictUserStatistics(userId);

        return toAnswerResult(answer, paperQuestion, question, correctAnswer);
    }

    @Transactional
    public ExamLearningSessionVO completeSession(Long sessionId, Long userId) {
        ExamLearningSession session = loadOwnedSession(sessionId, userId, true);
        ensureActive(session);
        ExamPaper paper = contextService.loadEligiblePaper(session.getExamPaperId(), userId);
        List<ExamQuestion> paperQuestions = contextService.loadPaperQuestions(paper);
        List<ExamLearningAnswer> answers = learningAnswerMapper.selectList(
                new LambdaQueryWrapper<ExamLearningAnswer>()
                        .eq(ExamLearningAnswer::getSessionId, sessionId));
        long answeredQuestions = answers.stream().map(ExamLearningAnswer::getQuestionId).distinct().count();
        if (answeredQuestions < paperQuestions.size()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "完成本轮学习前请先作答全部题目");
        }
        session.setStatus(COMPLETED);
        session.setActiveSessionKey(null);
        session.setCompleteTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return toSessionVO(session, paper, paperQuestions, answers);
    }

    private ExamLearningSession loadOwnedSession(Long sessionId, Long userId, boolean forUpdate) {
        ExamLearningSession session = forUpdate
                ? sessionMapper.selectByIdForUpdate(sessionId) : sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷学习会话不存在");
        }
        if (!userId.equals(session.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该学习会话");
        }
        return session;
    }

    private void ensureActive(ExamLearningSession session) {
        if (!Integer.valueOf(ACTIVE).equals(session.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "本轮试卷学习已完成");
        }
    }

    private ExamLearningSession findActiveSession(String activeKey) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<ExamLearningSession>()
                .eq(ExamLearningSession::getActiveSessionKey, activeKey));
    }

    private String activeKey(Long userId, Long paperId) {
        return "PAPER_LEARNING:" + userId + ":" + paperId;
    }

    private String buildCorrectAnswer(Question question, List<QuestionOption> options) {
        List<QuestionOption> correctOptions = options.stream()
                .filter(option -> Integer.valueOf(1).equals(option.getIsCorrect()))
                .toList();
        return answerEvaluator.buildCorrectAnswer(correctOptions, question.getQuestionType());
    }

    private int scoreOf(ExamQuestion paperQuestion) {
        return paperQuestion.getScore() != null ? paperQuestion.getScore() : 1;
    }

    private Long nextQuestionId(List<ExamQuestion> paperQuestions, Long currentQuestionId) {
        for (int index = 0; index < paperQuestions.size() - 1; index++) {
            if (currentQuestionId.equals(paperQuestions.get(index).getQuestionId())) {
                return paperQuestions.get(index + 1).getQuestionId();
            }
        }
        return currentQuestionId;
    }

    private void updateWrongQuestionAndReview(Long userId, Long questionId, String userAnswer, boolean correct) {
        try {
            if (correct) {
                wrongQuestionService.removeOnCorrect(userId, questionId);
            } else {
                wrongQuestionService.addWrongQuestion(userId, questionId, userAnswer);
            }
        } catch (Exception exception) {
            log.warn("试卷学习错题本处理失败: {}", exception.getMessage());
        }
        try {
            spacedRepetitionService.addToReviewPlan(userId, questionId);
        } catch (Exception exception) {
            log.warn("试卷学习加入复习计划失败: {}", exception.getMessage());
        }
    }

    private ExamLearningSessionVO toSessionVO(ExamLearningSession session, ExamPaper paper,
                                              List<ExamQuestion> paperQuestions,
                                              List<ExamLearningAnswer> answers) {
        Map<Long, ExamLearningAnswer> latestAnswers = new HashMap<>();
        for (ExamLearningAnswer answer : answers) {
            latestAnswers.merge(answer.getQuestionId(), answer, (previous, current) ->
                    current.getAttemptNo() >= previous.getAttemptNo() ? current : previous);
        }

        ExamLearningSessionVO vo = new ExamLearningSessionVO();
        vo.setId(session.getId());
        vo.setExamPaperId(paper.getId());
        vo.setPaperTitle(paper.getTitle());
        vo.setCourseId(paper.getCourseId());
        vo.setPaperType(paper.getPaperType());
        vo.setExamName(paper.getExamName());
        vo.setExamYear(paper.getExamYear());
        vo.setSourceReference(paper.getSourceReference());
        vo.setSourceVerified(Boolean.TRUE.equals(paper.getSourceVerified()));
        vo.setStatus(session.getStatus());
        vo.setCurrentQuestionId(session.getCurrentQuestionId());
        vo.setAnsweredQuestionCount(latestAnswers.size());
        vo.setCorrectQuestionCount((int) latestAnswers.values().stream()
                .filter(answer -> Integer.valueOf(1).equals(answer.getIsCorrect())).count());
        vo.setStartTime(session.getStartTime());
        vo.setCompleteTime(session.getCompleteTime());

        List<ExamLearningSessionVO.QuestionItem> items = new ArrayList<>();
        for (ExamQuestion paperQuestion : paperQuestions) {
            Question question = contextService.loadPaperQuestion(paper, paperQuestion);
            List<QuestionOption> options = contextService.loadOptions(question.getId());
            ExamLearningSessionVO.QuestionItem item = new ExamLearningSessionVO.QuestionItem();
            item.setQuestionId(question.getId());
            item.setSortOrder(paperQuestion.getSortOrder());
            item.setScore(scoreOf(paperQuestion));
            item.setContent(question.getContent());
            item.setQuestionType(question.getQuestionType());
            item.setSectionTitle(paperQuestion.getSectionTitle());
            item.setMajorQuestionNumber(paperQuestion.getMajorQuestionNumber());
            item.setMinorQuestionNumber(paperQuestion.getMinorQuestionNumber());
            item.setSubquestionNumber(paperQuestion.getSubquestionNumber());
            item.setDisplayNumber(paperQuestion.getDisplayNumber());
            item.setOptions(options.stream().map(option -> {
                QuestionOptionVO optionVO = QuestionOptionVO.fromEntity(option);
                optionVO.setIsCorrect(null);
                return optionVO;
            }).toList());
            ExamLearningAnswer latest = latestAnswers.get(question.getId());
            if (latest != null) {
                item.setLatestAnswer(toAnswerResult(latest, paperQuestion, question,
                        buildCorrectAnswer(question, options)));
            }
            items.add(item);
        }
        items.sort(Comparator.comparingInt(item -> item.getSortOrder() != null ? item.getSortOrder() : 0));
        vo.setQuestions(items);
        return vo;
    }

    private ExamLearningAnswerResultVO toAnswerResult(ExamLearningAnswer answer,
                                                       ExamQuestion paperQuestion,
                                                       Question question,
                                                       String correctAnswer) {
        ExamLearningAnswerResultVO result = new ExamLearningAnswerResultVO();
        result.setAnswerId(answer.getId());
        result.setQuestionId(answer.getQuestionId());
        result.setAttemptNo(answer.getAttemptNo());
        result.setUserAnswer(answer.getUserAnswer());
        boolean manualSelfReview = "SHORT_ANSWER".equals(question.getQuestionType());
        result.setCorrect(manualSelfReview ? null : Integer.valueOf(1).equals(answer.getIsCorrect()));
        result.setScore(answer.getScore());
        result.setFullScore(scoreOf(paperQuestion));
        result.setCorrectAnswer(manualSelfReview ? null : correctAnswer);
        result.setAnalysis(question.getAnalysis());
        result.setGradingStatus(manualSelfReview ? "SELF_REVIEW" : "AUTO_GRADED");
        result.setAnsweredAt(answer.getCreateTime());
        return result;
    }
}
