package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
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
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds user-visible exam record and answer projections without mutating exam state. */
@Service
public class ExamRecordViewService {

    private static final int ACTIVE = 0;
    private static final int COMPLETED = 1;
    private static final int PENDING_REVIEW = 3;

    private final ExamRecordMapper examRecordMapper;
    private final ExamAnswerMapper examAnswerMapper;
    private final ExamPaperMapper examPaperMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final AnswerEvaluator answerEvaluator;
    private final Clock clock;

    public ExamRecordViewService(ExamRecordMapper examRecordMapper,
                                 ExamAnswerMapper examAnswerMapper,
                                 ExamPaperMapper examPaperMapper,
                                 ExamQuestionMapper examQuestionMapper,
                                 QuestionMapper questionMapper,
                                 QuestionOptionMapper questionOptionMapper,
                                 AnswerEvaluator answerEvaluator) {
        this(examRecordMapper, examAnswerMapper, examPaperMapper, examQuestionMapper, questionMapper,
                questionOptionMapper, answerEvaluator, Clock.system(ExamSessionService.EXAM_ZONE));
    }

    ExamRecordViewService(ExamRecordMapper examRecordMapper,
                          ExamAnswerMapper examAnswerMapper,
                          ExamPaperMapper examPaperMapper,
                          ExamQuestionMapper examQuestionMapper,
                          QuestionMapper questionMapper,
                          QuestionOptionMapper questionOptionMapper,
                          AnswerEvaluator answerEvaluator,
                          Clock clock) {
        this.examRecordMapper = examRecordMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.examPaperMapper = examPaperMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.answerEvaluator = answerEvaluator;
        this.clock = clock;
    }

    public ExamRecordVO getExamResult(Long examRecordId, Long userId) {
        ExamRecord record = examRecordMapper.selectById(examRecordId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "考试记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        }
        if (record.getStatus() == null
                || (record.getStatus() != COMPLETED && record.getStatus() != PENDING_REVIEW)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "考试尚未完成");
        }
        ExamRecordVO view = toRecordVO(record, LocalDateTime.now(clock));
        view.setAnswers(toAnswerViews(record));
        return view;
    }

    ExamRecordVO toRecordVO(ExamRecord record, LocalDateTime serverTime) {
        ExamRecordVO view = new ExamRecordVO();
        view.setId(record.getId());
        view.setExamPaperId(record.getExamPaperId());
        view.setStartTime(record.getStartTime());
        view.setServerTime(serverTime.atZone(ExamSessionService.EXAM_ZONE).toOffsetDateTime());
        view.setEndTime(record.getEndTime());
        view.setScore(record.getScore());
        view.setTotalScore(record.getTotalScore());
        view.setStatus(record.getStatus());
        ExamPaper paper = examPaperMapper.selectById(record.getExamPaperId());
        if (paper != null) {
            fillPaperMetadata(view, paper);
            if (record.getStartTime() != null && paper.getDuration() != null) {
                LocalDateTime deadline = record.getStartTime().plusMinutes(paper.getDuration());
                view.setDeadline(deadline.atZone(ExamSessionService.EXAM_ZONE).toOffsetDateTime());
                if (record.getStatus() == ACTIVE && !serverTime.isBefore(deadline)) {
                    view.setStatus(ExamSessionService.TIMED_OUT);
                }
            }
        }
        return view;
    }

    private List<ExamRecordVO.ExamAnswerVO> toAnswerViews(ExamRecord record) {
        List<ExamAnswer> answers = examAnswerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                .eq(ExamAnswer::getExamRecordId, record.getId()));
        List<ExamQuestion> examQuestions = examQuestionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, record.getExamPaperId()));
        Map<Long, Integer> questionOrderMap = examQuestions.stream().collect(Collectors.toMap(
                ExamQuestion::getQuestionId, ExamQuestion::getSortOrder, (first, ignored) -> first));
        Map<Long, Integer> questionScoreMap = examQuestions.stream().collect(Collectors.toMap(
                ExamQuestion::getQuestionId, ExamQuestion::getScore, (first, ignored) -> first));
        Map<Long, ExamQuestion> questionMap = examQuestions.stream().collect(Collectors.toMap(
                ExamQuestion::getQuestionId, question -> question, (first, ignored) -> first));
        List<ExamRecordVO.ExamAnswerVO> views = new ArrayList<>();
        for (ExamAnswer answer : answers) {
            ExamRecordVO.ExamAnswerVO view = new ExamRecordVO.ExamAnswerVO();
            view.setQuestionId(answer.getQuestionId());
            view.setUserAnswer(answer.getUserAnswer());
            view.setIsCorrect(answer.getIsCorrect());
            view.setScore(answer.getScore());
            view.setGradingStatus(answer.getGradingStatus());
            view.setReviewComment(answer.getReviewComment());
            view.setReviewDetailJson(answer.getReviewDetailJson());
            view.setSortOrder(questionOrderMap.getOrDefault(answer.getQuestionId(), 0));
            view.setFullScore(questionScoreMap.getOrDefault(answer.getQuestionId(), 1));
            fillQuestionNumbering(view, questionMap.get(answer.getQuestionId()));
            fillQuestionContent(view, answer);
            views.add(view);
        }
        views.sort(Comparator.comparingInt(view -> view.getSortOrder() != null ? view.getSortOrder() : 0));
        return views;
    }

    private void fillPaperMetadata(ExamRecordVO view, ExamPaper paper) {
        view.setExamTitle(paper.getTitle());
        view.setCourseId(paper.getCourseId());
        view.setPaperType(paper.getPaperType());
        view.setExamName(paper.getExamName());
        view.setExamYear(paper.getExamYear());
        view.setSourceReference(paper.getSourceReference());
        view.setSourceVerified(paper.getSourceVerified());
        view.setDuration(paper.getDuration());
    }

    private void fillQuestionNumbering(ExamRecordVO.ExamAnswerVO view, ExamQuestion question) {
        if (question != null) {
            view.setSectionTitle(question.getSectionTitle());
            view.setMajorQuestionNumber(question.getMajorQuestionNumber());
            view.setMinorQuestionNumber(question.getMinorQuestionNumber());
            view.setSubquestionNumber(question.getSubquestionNumber());
            view.setDisplayNumber(question.getDisplayNumber());
        }
    }

    private void fillQuestionContent(ExamRecordVO.ExamAnswerVO view, ExamAnswer answer) {
        Question question = questionMapper.selectById(answer.getQuestionId());
        if (question == null) {
            return;
        }
        view.setContent(question.getContent());
        view.setQuestionType(question.getQuestionType());
        boolean pending = "PENDING".equals(answer.getGradingStatus());
        view.setAnalysis(pending ? null : question.getAnalysis());
        List<QuestionOption> correctOptions = questionOptionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                        .eq(QuestionOption::getQuestionId, question.getId())
                        .orderByAsc(QuestionOption::getSortOrder))
                .stream().filter(option -> option.getIsCorrect() != null && option.getIsCorrect() == 1)
                .collect(Collectors.toList());
        view.setCorrectAnswer(pending ? null
                : answerEvaluator.buildCorrectAnswer(correctOptions, question.getQuestionType()));
    }
}
