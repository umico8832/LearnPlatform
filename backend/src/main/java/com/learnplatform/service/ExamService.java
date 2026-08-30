package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.ExamTimedOutException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.entity.ExamAnswer;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.ExamRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.ExamAnswerMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考试服务（用户端）
 */
@Service
public class ExamService {

    private static final Logger log = LoggerFactory.getLogger(ExamService.class);
    private static final int ACTIVE = 0;
    private static final int COMPLETED = 1;
    private static final int TIMED_OUT = 2;
    private static final int PENDING_REVIEW = 3;
    private static final ZoneId EXAM_ZONE = ZoneId.of("Asia/Shanghai");

    private final ExamRecordMapper examRecordMapper;
    private final ExamAnswerMapper examAnswerMapper;
    private final ExamPaperMapper examPaperMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final WrongQuestionService wrongQuestionService;
    private final AnswerEvaluator answerEvaluator;
    private final CacheEvictService cacheEvictService;
    private final CourseLearningEventService courseLearningEventService;
    private final Clock clock;

    public ExamService(ExamRecordMapper examRecordMapper,
                       ExamAnswerMapper examAnswerMapper,
                       ExamPaperMapper examPaperMapper,
                       ExamQuestionMapper examQuestionMapper,
                       QuestionMapper questionMapper,
                       QuestionOptionMapper questionOptionMapper,
                       WrongQuestionService wrongQuestionService,
                       AnswerEvaluator answerEvaluator,
                       CacheEvictService cacheEvictService) {
        this(examRecordMapper, examAnswerMapper, examPaperMapper, examQuestionMapper, questionMapper,
                questionOptionMapper, wrongQuestionService, answerEvaluator, cacheEvictService, null);
    }

    @Autowired
    public ExamService(ExamRecordMapper examRecordMapper,
                       ExamAnswerMapper examAnswerMapper,
                       ExamPaperMapper examPaperMapper,
                       ExamQuestionMapper examQuestionMapper,
                       QuestionMapper questionMapper,
                       QuestionOptionMapper questionOptionMapper,
                       WrongQuestionService wrongQuestionService,
                       AnswerEvaluator answerEvaluator,
                       CacheEvictService cacheEvictService,
                       CourseLearningEventService courseLearningEventService) {
        this(examRecordMapper, examAnswerMapper, examPaperMapper, examQuestionMapper, questionMapper,
                questionOptionMapper, wrongQuestionService, answerEvaluator, cacheEvictService,
                courseLearningEventService, Clock.system(EXAM_ZONE));
    }

    /**
     * 测试专用构造：允许注入固定 Clock，使考试时间语义不依赖机器系统时区或真实墙钟。
     */
    ExamService(ExamRecordMapper examRecordMapper,
                ExamAnswerMapper examAnswerMapper,
                ExamPaperMapper examPaperMapper,
                ExamQuestionMapper examQuestionMapper,
                QuestionMapper questionMapper,
                QuestionOptionMapper questionOptionMapper,
                WrongQuestionService wrongQuestionService,
                AnswerEvaluator answerEvaluator,
                CacheEvictService cacheEvictService,
                CourseLearningEventService courseLearningEventService,
                Clock clock) {
        this.examRecordMapper = examRecordMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.examPaperMapper = examPaperMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.wrongQuestionService = wrongQuestionService;
        this.answerEvaluator = answerEvaluator;
        this.cacheEvictService = cacheEvictService;
        this.courseLearningEventService = courseLearningEventService;
        this.clock = clock;
    }

    /**
     * 获取已发布的试卷列表
     */
    public Page<ExamRecordVO> getExamList(Long userId, int pageNum, int pageSize) {
        Page<ExamRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExamRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamRecord::getUserId, userId);
        wrapper.orderByDesc(ExamRecord::getCreateTime);
        Page<ExamRecord> result = examRecordMapper.selectPage(page, wrapper);

        Page<ExamRecordVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        LocalDateTime serverTime = currentExamTime();
        voPage.setRecords(result.getRecords().stream()
                .map(record -> toVO(record, serverTime))
                .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 开始考试（创建考试记录）
     */
    @Transactional
    public ExamRecordVO startExam(Long examPaperId, Long userId) {
        log.info("开始考试: userId={}, examPaperId={}", userId, examPaperId);
        ExamPaper paper = examPaperMapper.selectById(examPaperId);
        if (paper == null) { throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在"); }
        if (!canAccessPaper(paper, userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        if (paper.getStatus() == null || paper.getStatus() != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "试卷未发布");
        }

        String activeKey = activeExamKey(userId, examPaperId);
        LocalDateTime now = currentExamTime();
        ExamRecord existing = lockActiveRecord(activeKey);
        if (existing != null) {
            if (!isExpired(existing, paper, now)) {
                return toVO(existing, now);
            }
            markTimedOut(existing, now);
        }

        ExamRecord record = new ExamRecord();
        record.setUserId(userId);
        record.setExamPaperId(examPaperId);
        record.setStartTime(now);
        record.setTotalScore(paper.getTotalScore());
        record.setStatus(ACTIVE);
        record.setActiveExamKey(activeKey);
        try {
            examRecordMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            // A locking read is required here: the earlier consistent read may not
            // see the row that won the unique-key race under REPEATABLE READ.
            ExamRecord concurrent = examRecordMapper.selectByActiveExamKeyForUpdate(activeKey);
            if (concurrent == null || concurrent.getStatus() == null || concurrent.getStatus() != ACTIVE) {
                throw exception;
            }
            return toVO(concurrent, currentExamTime());
        }

        return toVO(record, now);
    }

    private boolean canAccessPaper(ExamPaper paper, Long userId) {
        return paper.getVisibility() == null || "PUBLIC".equals(paper.getVisibility())
                || ("PRIVATE".equals(paper.getVisibility()) && userId.equals(paper.getOwnerUserId()));
    }

    /**
     * 获取本人考试会话的服务端权威时间与试卷引用，不返回答案。
     */
    @Transactional
    public ExamRecordVO getExamSession(Long examRecordId, Long userId) {
        ExamRecord record = examRecordMapper.selectByIdForUpdate(examRecordId);
        if (record == null) { throw new BusinessException(ResultCode.NOT_FOUND, "考试记录不存在"); }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        }
        ExamPaper paper = examPaperMapper.selectById(record.getExamPaperId());
        if (paper == null) { throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在"); }
        LocalDateTime now = currentExamTime();
        if (record.getStatus() == ACTIVE && isExpired(record, paper, now)) {
            markTimedOut(record, now);
        }
        return toVO(record, now);
    }

    /**
     * 提交考试
     */
    @Transactional(noRollbackFor = ExamTimedOutException.class)
    public ExamRecordVO submitExam(ExamSubmitRequest request, Long userId) {
        log.info("提交考试: userId={}, examRecordId={}", userId, request.getExamRecordId());
        ExamRecord record = examRecordMapper.selectByIdForUpdate(request.getExamRecordId());
        if (record == null) { throw new BusinessException(ResultCode.NOT_FOUND, "考试记录不存在"); }
        if (!record.getUserId().equals(userId)) { throw new BusinessException(ResultCode.FORBIDDEN, "无权操作"); }
        if (record.getStatus() != 0) { throw new BusinessException(ResultCode.BUSINESS_ERROR, "考试已结束"); }

        ExamPaper paper = examPaperMapper.selectById(record.getExamPaperId());
        if (paper == null) { throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在"); }

        LocalDateTime now = currentExamTime();
        if (isExpired(record, paper, now)) {
            markTimedOut(record, now);
            throw new ExamTimedOutException();
        }

        // 获取试卷题目关联
        LambdaQueryWrapper<ExamQuestion> eqWrapper = new LambdaQueryWrapper<>();
        eqWrapper.eq(ExamQuestion::getExamPaperId, paper.getId());
        List<ExamQuestion> examQuestions = examQuestionMapper.selectList(eqWrapper);
        Map<Long, Integer> questionScoreMap = examQuestions.stream()
                .collect(Collectors.toMap(ExamQuestion::getQuestionId, ExamQuestion::getScore, (a, b) -> a));

        int totalScore = examQuestions.stream()
                .mapToInt(eq -> eq.getScore() != null ? eq.getScore() : 1)
                .sum();
        int earnedScore = 0;
        boolean hasPendingReview = false;

        Map<Long, String> submittedAnswers = new HashMap<>();
        for (ExamSubmitRequest.AnswerItem answerItem : request.getAnswers()) {
            Long questionId = answerItem.getQuestionId();
            if (questionId == null || !questionScoreMap.containsKey(questionId)) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "提交内容包含非本试卷题目");
            }
            if (submittedAnswers.put(questionId, answerItem.getUserAnswer()) != null) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "同一道题不能重复提交");
            }
        }

        // 处理每道题的答案
        if (request.getAnswers() != null) {
            for (ExamQuestion examQuestion : examQuestions) {
                Long questionId = examQuestion.getQuestionId();
                Question question = questionMapper.selectById(questionId);
                if (question == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "试卷题目不存在");
                }

                int questionScore = questionScoreMap.getOrDefault(questionId, 1);
                String userAnswer = submittedAnswers.getOrDefault(questionId, "");

                // 获取正确答案
                LambdaQueryWrapper<QuestionOption> optWrapper = new LambdaQueryWrapper<>();
                optWrapper.eq(QuestionOption::getQuestionId, question.getId())
                         .orderByAsc(QuestionOption::getSortOrder);
                List<QuestionOption> options = questionOptionMapper.selectList(optWrapper);
                List<QuestionOption> correctOptions = options.stream()
                        .filter(o -> o.getIsCorrect() != null && o.getIsCorrect() == 1)
                        .collect(Collectors.toList());
                String correctAnswer = answerEvaluator.buildCorrectAnswer(correctOptions, question.getQuestionType());

                boolean manualGrading = "SHORT_ANSWER".equals(question.getQuestionType());
                boolean isCorrect = !manualGrading && answerEvaluator.isCorrect(question.getQuestionType(),
                        userAnswer != null ? userAnswer.trim() : "", correctAnswer);

                if (isCorrect) { earnedScore += questionScore; }
                if (manualGrading) { hasPendingReview = true; }

                // 保存答题详情
                ExamAnswer examAnswer = new ExamAnswer();
                examAnswer.setExamRecordId(record.getId());
                examAnswer.setQuestionId(questionId);
                examAnswer.setUserAnswer(userAnswer != null ? userAnswer : "");
                examAnswer.setIsCorrect(manualGrading ? null : (isCorrect ? 1 : 0));
                examAnswer.setScore(manualGrading ? null : (isCorrect ? questionScore : 0));
                examAnswer.setGradingStatus(manualGrading ? "PENDING" : "AUTO_GRADED");
                examAnswerMapper.insert(examAnswer);
                if (!manualGrading && courseLearningEventService != null) {
                    courseLearningEventService.recordQuestionAnswer(userId, question, "EXAM_ANSWERED", "EXAM",
                            examAnswer.getId(), isCorrect, examAnswer.getCreateTime());
                }

                // 错题自动加入错题本
                try {
                    if (manualGrading) {
                        continue;
                    }
                    if (isCorrect) {
                        wrongQuestionService.removeOnCorrect(userId, questionId);
                    } else {
                        wrongQuestionService.addWrongQuestion(userId, questionId, userAnswer);
                    }
                } catch (Exception e) {
                    log.warn("考试错题本处理失败: {}", e.getMessage());
                }
            }
        }

        // 更新考试记录
        log.info("考试判分完成: userId={}, examRecordId={}, score={}/{}", userId, record.getId(), earnedScore, totalScore);
        record.setEndTime(currentExamTime());
        record.setScore(earnedScore);
        record.setTotalScore(totalScore);
        record.setStatus(hasPendingReview ? PENDING_REVIEW : COMPLETED);
        record.setActiveExamKey(null);
        examRecordMapper.updateById(record);

        // 清除统计缓存
        cacheEvictService.evictUserStatistics(userId);

        return getExamResult(record.getId(), userId);
    }

    /**
     * 获取考试结果
     */
    public ExamRecordVO getExamResult(Long examRecordId, Long userId) {
        ExamRecord record = examRecordMapper.selectById(examRecordId);
        if (record == null) { throw new BusinessException(ResultCode.NOT_FOUND, "考试记录不存在"); }
        if (!record.getUserId().equals(userId)) { throw new BusinessException(ResultCode.FORBIDDEN, "无权操作"); }
        if (record.getStatus() == null
                || (record.getStatus() != COMPLETED && record.getStatus() != PENDING_REVIEW)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "考试尚未完成");
        }

        ExamRecordVO vo = toVO(record, currentExamTime());

        // 获取答题详情
        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getExamRecordId, record.getId());
        List<ExamAnswer> answers = examAnswerMapper.selectList(answerWrapper);

        // 获取试卷题目顺序
        LambdaQueryWrapper<ExamQuestion> eqWrapper = new LambdaQueryWrapper<>();
        eqWrapper.eq(ExamQuestion::getExamPaperId, record.getExamPaperId());
        List<ExamQuestion> examQuestions = examQuestionMapper.selectList(eqWrapper);
        Map<Long, Integer> questionOrderMap = examQuestions.stream()
                .collect(Collectors.toMap(ExamQuestion::getQuestionId, ExamQuestion::getSortOrder, (a, b) -> a));
        Map<Long, Integer> questionScoreMap = examQuestions.stream()
                .collect(Collectors.toMap(ExamQuestion::getQuestionId, ExamQuestion::getScore, (a, b) -> a));
        Map<Long, ExamQuestion> examQuestionMap = examQuestions.stream()
                .collect(Collectors.toMap(ExamQuestion::getQuestionId, question -> question, (a, b) -> a));

        List<ExamRecordVO.ExamAnswerVO> answerVOs = new ArrayList<>();
        for (ExamAnswer answer : answers) {
            ExamRecordVO.ExamAnswerVO avo = new ExamRecordVO.ExamAnswerVO();
            avo.setQuestionId(answer.getQuestionId());
            avo.setUserAnswer(answer.getUserAnswer());
            avo.setIsCorrect(answer.getIsCorrect());
            avo.setScore(answer.getScore());
            avo.setGradingStatus(answer.getGradingStatus());
            avo.setReviewComment(answer.getReviewComment());
            avo.setReviewDetailJson(answer.getReviewDetailJson());
            avo.setSortOrder(questionOrderMap.getOrDefault(answer.getQuestionId(), 0));
            avo.setFullScore(questionScoreMap.getOrDefault(answer.getQuestionId(), 1));

            ExamQuestion examQuestion = examQuestionMap.get(answer.getQuestionId());
            if (examQuestion != null) {
                avo.setSectionTitle(examQuestion.getSectionTitle());
                avo.setMajorQuestionNumber(examQuestion.getMajorQuestionNumber());
                avo.setMinorQuestionNumber(examQuestion.getMinorQuestionNumber());
                avo.setSubquestionNumber(examQuestion.getSubquestionNumber());
                avo.setDisplayNumber(examQuestion.getDisplayNumber());
            }

            Question question = questionMapper.selectById(answer.getQuestionId());
            if (question != null) {
                avo.setContent(question.getContent());
                avo.setQuestionType(question.getQuestionType());
                boolean pending = "PENDING".equals(answer.getGradingStatus());
                avo.setAnalysis(pending ? null : question.getAnalysis());

                LambdaQueryWrapper<QuestionOption> optWrapper = new LambdaQueryWrapper<>();
                optWrapper.eq(QuestionOption::getQuestionId, question.getId())
                         .orderByAsc(QuestionOption::getSortOrder);
                List<QuestionOption> correctOptions = questionOptionMapper.selectList(optWrapper).stream()
                        .filter(o -> o.getIsCorrect() != null && o.getIsCorrect() == 1)
                        .collect(Collectors.toList());
                avo.setCorrectAnswer(pending ? null
                        : answerEvaluator.buildCorrectAnswer(correctOptions, question.getQuestionType()));
            }
            answerVOs.add(avo);
        }

        answerVOs.sort(Comparator.comparingInt(a -> a.getSortOrder() != null ? a.getSortOrder() : 0));
        vo.setAnswers(answerVOs);

        return vo;
    }

    // ======================== 私有方法 ========================

    private ExamRecordVO toVO(ExamRecord record, LocalDateTime serverTime) {
        ExamRecordVO vo = new ExamRecordVO();
        vo.setId(record.getId());
        vo.setExamPaperId(record.getExamPaperId());
        vo.setStartTime(record.getStartTime());
        vo.setServerTime(serverTime.atZone(EXAM_ZONE).toOffsetDateTime());
        vo.setEndTime(record.getEndTime());
        vo.setScore(record.getScore());
        vo.setTotalScore(record.getTotalScore());
        vo.setStatus(record.getStatus());

        ExamPaper paper = examPaperMapper.selectById(record.getExamPaperId());
        if (paper != null) {
            vo.setExamTitle(paper.getTitle());
            vo.setCourseId(paper.getCourseId());
            vo.setPaperType(paper.getPaperType());
            vo.setExamName(paper.getExamName());
            vo.setExamYear(paper.getExamYear());
            vo.setSourceReference(paper.getSourceReference());
            vo.setSourceVerified(paper.getSourceVerified());
            vo.setDuration(paper.getDuration());
            if (record.getStartTime() != null && paper.getDuration() != null) {
                LocalDateTime deadline = record.getStartTime().plusMinutes(paper.getDuration());
                vo.setDeadline(deadline.atZone(EXAM_ZONE).toOffsetDateTime());
                if (record.getStatus() == ACTIVE && !serverTime.isBefore(deadline)) {
                    vo.setStatus(TIMED_OUT);
                }
            }
        }
        return vo;
    }

    private String activeExamKey(Long userId, Long examPaperId) {
        return "EXAM:" + userId + ":" + examPaperId;
    }

    private ExamRecord lockActiveRecord(String activeKey) {
        ExamRecord candidate = examRecordMapper.selectByActiveExamKey(activeKey);
        if (candidate == null) {
            return null;
        }
        ExamRecord locked = examRecordMapper.selectByIdForUpdate(candidate.getId());
        if (locked == null || locked.getStatus() == null || locked.getStatus() != ACTIVE
                || !activeKey.equals(locked.getActiveExamKey())) {
            return null;
        }
        return locked;
    }

    private LocalDateTime currentExamTime() {
        return LocalDateTime.now(clock);
    }

    private boolean isExpired(ExamRecord record, ExamPaper paper, LocalDateTime now) {
        int duration = paper.getDuration() != null ? paper.getDuration() : 60;
        return record.getStartTime() == null
                || !now.isBefore(record.getStartTime().plusMinutes(duration));
    }

    private void markTimedOut(ExamRecord record, LocalDateTime now) {
        record.setEndTime(now);
        record.setScore(0);
        record.setStatus(TIMED_OUT);
        record.setActiveExamKey(null);
        examRecordMapper.updateById(record);
    }

}
