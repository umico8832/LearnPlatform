package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.SubjectiveAnswerReviewVO;
import com.learnplatform.dto.SubjectiveGradingRequest;
import com.learnplatform.entity.ExamAnswer;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.ExamRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.SubjectiveGradingPoint;
import com.learnplatform.mapper.ExamAnswerMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.SubjectiveGradingPointMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SubjectiveExamGradingService {
    private final ExamAnswerMapper answerMapper;
    private final ExamRecordMapper recordMapper;
    private final ExamPaperMapper paperMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionMapper questionMapper;
    private final SubjectiveGradingPointMapper gradingPointMapper;
    private final ObjectMapper objectMapper;
    private final CacheEvictService cacheEvictService;

    public SubjectiveExamGradingService(ExamAnswerMapper answerMapper, ExamRecordMapper recordMapper,
                                        ExamPaperMapper paperMapper, ExamQuestionMapper examQuestionMapper,
                                        QuestionMapper questionMapper, SubjectiveGradingPointMapper gradingPointMapper,
                                        ObjectMapper objectMapper, CacheEvictService cacheEvictService) {
        this.answerMapper = answerMapper;
        this.recordMapper = recordMapper;
        this.paperMapper = paperMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionMapper = questionMapper;
        this.gradingPointMapper = gradingPointMapper;
        this.objectMapper = objectMapper;
        this.cacheEvictService = cacheEvictService;
    }

    public List<SubjectiveAnswerReviewVO> listPending() {
        return answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                        .eq(ExamAnswer::getGradingStatus, "PENDING")
                        .orderByAsc(ExamAnswer::getCreateTime))
                .stream().map(this::toReviewVO).toList();
    }

    @Transactional
    public SubjectiveAnswerReviewVO grade(Long answerId, SubjectiveGradingRequest request, Long reviewerId) {
        ExamAnswer answer = answerMapper.selectByIdForUpdate(answerId);
        if (answer == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "待批阅答案不存在");
        }
        if (!"PENDING".equals(answer.getGradingStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该答案已完成批阅");
        }
        ExamRecord record = recordMapper.selectByIdForUpdate(answer.getExamRecordId());
        if (record == null || !Integer.valueOf(3).equals(record.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "考试记录不在待批阅状态");
        }

        List<SubjectiveGradingPoint> rubric = loadRubric(answer.getQuestionId());
        Map<String, SubjectiveGradingPoint> rubricByKey = rubric.stream()
                .collect(Collectors.toMap(SubjectiveGradingPoint::getPointKey, point -> point));
        List<SubjectiveGradingRequest.PointScore> submitted = request == null ? null : request.getPoints();
        if (submitted == null || submitted.size() != rubric.size()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "必须逐项提交全部评分点");
        }
        Set<String> submittedKeys = submitted.stream().map(SubjectiveGradingRequest.PointScore::getPointKey)
                .collect(Collectors.toSet());
        if (submittedKeys.size() != submitted.size() || !submittedKeys.equals(rubricByKey.keySet())) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "评分点缺失、重复或不属于该题");
        }

        int awarded = 0;
        for (SubjectiveGradingRequest.PointScore pointScore : submitted) {
            SubjectiveGradingPoint point = rubricByKey.get(pointScore.getPointKey());
            Integer value = pointScore.getAwardedScore();
            if (value == null || value < 0 || value > point.getMaxScore()) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR,
                        "评分点 " + point.getTitle() + " 的得分超出范围");
            }
            awarded += value;
        }

        ExamQuestion relation = loadExamQuestion(record.getExamPaperId(), answer.getQuestionId());
        int fullScore = relation.getScore() != null ? relation.getScore() : 1;
        int rubricTotal = rubric.stream().mapToInt(SubjectiveGradingPoint::getMaxScore).sum();
        if (rubricTotal != fullScore) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "评分点总分与试卷题目分值不一致");
        }

        answer.setScore(awarded);
        answer.setIsCorrect(awarded == fullScore ? 1 : 0);
        answer.setGradingStatus("REVIEWED");
        answer.setReviewerId(reviewerId);
        answer.setReviewComment(request.getReviewComment());
        answer.setReviewDetailJson(writeReviewDetail(submitted));
        answer.setReviewedAt(LocalDateTime.now());
        answerMapper.updateById(answer);

        List<ExamAnswer> allAnswers = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                .eq(ExamAnswer::getExamRecordId, record.getId()));
        int finalScore = allAnswers.stream().map(ExamAnswer::getScore).filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        boolean stillPending = allAnswers.stream().anyMatch(item -> "PENDING".equals(item.getGradingStatus()));
        record.setScore(finalScore);
        record.setStatus(stillPending ? 3 : 1);
        recordMapper.updateById(record);
        cacheEvictService.evictUserStatistics(record.getUserId());
        return toReviewVO(answer);
    }

    private List<SubjectiveGradingPoint> loadRubric(Long questionId) {
        List<SubjectiveGradingPoint> rubric = gradingPointMapper.selectList(
                new LambdaQueryWrapper<SubjectiveGradingPoint>()
                        .eq(SubjectiveGradingPoint::getQuestionId, questionId)
                        .orderByAsc(SubjectiveGradingPoint::getSortOrder));
        if (rubric.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "主观题尚未配置评分点");
        }
        return rubric;
    }

    private ExamQuestion loadExamQuestion(Long paperId, Long questionId) {
        ExamQuestion relation = examQuestionMapper.selectOne(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, paperId)
                .eq(ExamQuestion::getQuestionId, questionId));
        if (relation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷题目关系不存在");
        }
        return relation;
    }

    private SubjectiveAnswerReviewVO toReviewVO(ExamAnswer answer) {
        ExamRecord record = recordMapper.selectById(answer.getExamRecordId());
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "考试记录不存在");
        }
        ExamQuestion relation = loadExamQuestion(record.getExamPaperId(), answer.getQuestionId());
        Question question = questionMapper.selectById(answer.getQuestionId());
        ExamPaper paper = paperMapper.selectById(record.getExamPaperId());
        if (question == null || paper == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "主观题或试卷不存在");
        }
        SubjectiveAnswerReviewVO vo = new SubjectiveAnswerReviewVO();
        vo.setAnswerId(answer.getId());
        vo.setExamRecordId(record.getId());
        vo.setUserId(record.getUserId());
        vo.setExamTitle(paper.getTitle());
        vo.setDisplayNumber(relation.getDisplayNumber());
        vo.setContent(question.getContent());
        vo.setUserAnswer(answer.getUserAnswer());
        vo.setFullScore(relation.getScore());
        vo.setGradingStatus(answer.getGradingStatus());
        vo.setScore(answer.getScore());
        vo.setReviewComment(answer.getReviewComment());
        vo.setReviewDetailJson(answer.getReviewDetailJson());
        vo.setSubmittedAt(answer.getCreateTime());
        vo.setGradingPoints(loadRubric(answer.getQuestionId()).stream().map(point -> {
            SubjectiveAnswerReviewVO.GradingPointVO item = new SubjectiveAnswerReviewVO.GradingPointVO();
            item.setPointKey(point.getPointKey());
            item.setTitle(point.getTitle());
            item.setDescription(point.getDescription());
            item.setReferenceAnswer(point.getReferenceAnswer());
            item.setMaxScore(point.getMaxScore());
            item.setSortOrder(point.getSortOrder());
            return item;
        }).toList());
        return vo;
    }

    private String writeReviewDetail(List<SubjectiveGradingRequest.PointScore> scores) {
        try {
            return objectMapper.writeValueAsString(scores);
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "评分明细保存失败");
        }
    }
}
