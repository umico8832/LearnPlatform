package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.ExamTimedOutException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.dto.QuestionOptionVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 考试服务（用户端）
 */
@Service
public class ExamService {

    private static final Logger log = LoggerFactory.getLogger(ExamService.class);

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
        voPage.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 开始考试（创建考试记录）
     */
    @Transactional
    public ExamRecordVO startExam(Long examPaperId, Long userId) {
        log.info("开始考试: userId={}, examPaperId={}", userId, examPaperId);
        ExamPaper paper = examPaperMapper.selectById(examPaperId);
        if (paper == null) throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        if (paper.getStatus() == null || paper.getStatus() != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "试卷未发布");
        }

        ExamRecord record = new ExamRecord();
        record.setUserId(userId);
        record.setExamPaperId(examPaperId);
        record.setStartTime(LocalDateTime.now());
        record.setTotalScore(paper.getTotalScore());
        record.setStatus(0); // 进行中
        examRecordMapper.insert(record);

        return toVO(record);
    }

    /**
     * 提交考试
     */
    @Transactional(noRollbackFor = ExamTimedOutException.class)
    public ExamRecordVO submitExam(ExamSubmitRequest request, Long userId) {
        log.info("提交考试: userId={}, examRecordId={}", userId, request.getExamRecordId());
        ExamRecord record = examRecordMapper.selectByIdForUpdate(request.getExamRecordId());
        if (record == null) throw new BusinessException(ResultCode.NOT_FOUND, "考试记录不存在");
        if (!record.getUserId().equals(userId)) throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        if (record.getStatus() != 0) throw new BusinessException(ResultCode.BUSINESS_ERROR, "考试已结束");

        ExamPaper paper = examPaperMapper.selectById(record.getExamPaperId());
        if (paper == null) throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");

        LocalDateTime deadline = record.getStartTime().plusMinutes(paper.getDuration());
        if (LocalDateTime.now().isAfter(deadline)) {
            record.setEndTime(LocalDateTime.now());
            record.setScore(0);
            record.setStatus(2);
            examRecordMapper.updateById(record);
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

                boolean isCorrect = answerEvaluator.isCorrect(question.getQuestionType(),
                        userAnswer != null ? userAnswer.trim() : "",
                        correctAnswer);

                if (isCorrect) earnedScore += questionScore;

                // 保存答题详情
                ExamAnswer examAnswer = new ExamAnswer();
                examAnswer.setExamRecordId(record.getId());
                examAnswer.setQuestionId(questionId);
                examAnswer.setUserAnswer(userAnswer != null ? userAnswer : "");
                examAnswer.setIsCorrect(isCorrect ? 1 : 0);
                examAnswer.setScore(isCorrect ? questionScore : 0);
                examAnswerMapper.insert(examAnswer);
                if (courseLearningEventService != null) {
                    courseLearningEventService.recordQuestionAnswer(userId, question, "EXAM_ANSWERED", "EXAM",
                            examAnswer.getId(), isCorrect, examAnswer.getCreateTime());
                }

                // 错题自动加入错题本
                try {
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
        record.setEndTime(LocalDateTime.now());
        record.setScore(earnedScore);
        record.setTotalScore(totalScore);
        record.setStatus(1); // 已完成
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
        if (record == null) throw new BusinessException(ResultCode.NOT_FOUND, "考试记录不存在");
        if (!record.getUserId().equals(userId)) throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");

        ExamRecordVO vo = toVO(record);

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

        List<ExamRecordVO.ExamAnswerVO> answerVOs = new ArrayList<>();
        for (ExamAnswer answer : answers) {
            ExamRecordVO.ExamAnswerVO avo = new ExamRecordVO.ExamAnswerVO();
            avo.setQuestionId(answer.getQuestionId());
            avo.setUserAnswer(answer.getUserAnswer());
            avo.setIsCorrect(answer.getIsCorrect());
            avo.setScore(answer.getScore());
            avo.setSortOrder(questionOrderMap.getOrDefault(answer.getQuestionId(), 0));
            avo.setFullScore(questionScoreMap.getOrDefault(answer.getQuestionId(), 1));

            Question question = questionMapper.selectById(answer.getQuestionId());
            if (question != null) {
                avo.setContent(question.getContent());
                avo.setQuestionType(question.getQuestionType());
                avo.setAnalysis(question.getAnalysis());

                LambdaQueryWrapper<QuestionOption> optWrapper = new LambdaQueryWrapper<>();
                optWrapper.eq(QuestionOption::getQuestionId, question.getId())
                         .orderByAsc(QuestionOption::getSortOrder);
                List<QuestionOption> correctOptions = questionOptionMapper.selectList(optWrapper).stream()
                        .filter(o -> o.getIsCorrect() != null && o.getIsCorrect() == 1)
                        .collect(Collectors.toList());
                avo.setCorrectAnswer(answerEvaluator.buildCorrectAnswer(correctOptions, question.getQuestionType()));
            }
            answerVOs.add(avo);
        }

        answerVOs.sort(Comparator.comparingInt(a -> a.getSortOrder() != null ? a.getSortOrder() : 0));
        vo.setAnswers(answerVOs);

        return vo;
    }

    // ======================== 私有方法 ========================

    private ExamRecordVO toVO(ExamRecord record) {
        ExamRecordVO vo = new ExamRecordVO();
        vo.setId(record.getId());
        vo.setExamPaperId(record.getExamPaperId());
        vo.setStartTime(record.getStartTime());
        vo.setEndTime(record.getEndTime());
        vo.setScore(record.getScore());
        vo.setTotalScore(record.getTotalScore());
        vo.setStatus(record.getStatus());

        ExamPaper paper = examPaperMapper.selectById(record.getExamPaperId());
        if (paper != null) {
            vo.setExamTitle(paper.getTitle());
            vo.setDuration(paper.getDuration());
        }
        return vo;
    }

}
