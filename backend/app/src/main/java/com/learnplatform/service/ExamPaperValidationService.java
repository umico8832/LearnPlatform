package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamPaperCreateRequest;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.SubjectiveGradingPoint;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.SubjectiveGradingPointMapper;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ExamPaperValidationService {
    private static final String DEFAULT_PAPER_TYPE = "PRACTICE";
    private static final String OFFICIAL_PAPER_TYPE = "OFFICIAL_EXAM";
    private static final Set<String> PAPER_TYPES = Set.of(DEFAULT_PAPER_TYPE, OFFICIAL_PAPER_TYPE);

    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionMapper questionMapper;
    private final SubjectiveGradingPointMapper subjectiveGradingPointMapper;

    public ExamPaperValidationService(ExamQuestionMapper examQuestionMapper,
                                      QuestionMapper questionMapper,
                                      SubjectiveGradingPointMapper subjectiveGradingPointMapper) {
        this.examQuestionMapper = examQuestionMapper;
        this.questionMapper = questionMapper;
        this.subjectiveGradingPointMapper = subjectiveGradingPointMapper;
    }

    public void ensureDraft(ExamPaper paper, String message) {
        if (paper.getStatus() != null && paper.getStatus() == 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, message);
        }
    }

    public void ensurePublishable(Integer requestedStatus,
                                  List<ExamPaperCreateRequest.QuestionItem> questions,
                                  Integer currentQuestionCount) {
        if (requestedStatus == null || requestedStatus != 1) { return; }
        int questionCount = questions != null ? questions.size()
                : (currentQuestionCount != null ? currentQuestionCount : 0);
        if (questionCount <= 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "空试卷不能发布");
        }
    }

    public void ensureOfficialRequestReady(Integer status, String paperType, String examName,
                                           Integer examYear, String sourceReference, Boolean sourceVerified,
                                           List<ExamPaperCreateRequest.QuestionItem> questions, Long paperId) {
        if (status == null || status != 1 || !OFFICIAL_PAPER_TYPE.equals(paperType)) { return; }
        ensureOfficialMetadata(examName, examYear, sourceReference, sourceVerified);
        if (questions != null) {
            if (questions.stream().anyMatch(item -> item == null || isBlank(item.getDisplayNumber()))) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "官方试卷每道题必须填写展示题号");
            }
            return;
        }
        if (paperId != null) { ensureOfficialQuestionNumbers(findExamQuestions(paperId)); }
    }

    public void ensureOfficialPaperReady(ExamPaper paper) {
        String paperType = normalizePaperType(paper.getPaperType());
        ensurePaperTypeSupported(paperType);
        if (!OFFICIAL_PAPER_TYPE.equals(paperType)) { return; }
        ensureOfficialMetadata(paper.getExamName(), paper.getExamYear(), paper.getSourceReference(),
                paper.getSourceVerified());
        ensureOfficialQuestionNumbers(findExamQuestions(paper.getId()));
    }

    public void ensureManualGradingReady(Integer status,
                                         List<ExamPaperCreateRequest.QuestionItem> requestedQuestions,
                                         Long paperId) {
        if (status == null || status != 1) { return; }
        if (requestedQuestions != null) {
            for (ExamPaperCreateRequest.QuestionItem item : requestedQuestions) {
                ensureQuestionRubric(item.getQuestionId(), item.getScore() != null ? item.getScore() : 1);
            }
            return;
        }
        if (paperId != null) {
            for (ExamQuestion relation : findExamQuestions(paperId)) {
                ensureQuestionRubric(relation.getQuestionId(),
                        relation.getScore() != null ? relation.getScore() : 1);
            }
        }
    }

    public void ensurePaperTypeSupported(String paperType) {
        if (!PAPER_TYPES.contains(paperType)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "不支持的试卷类型");
        }
    }

    public static String normalizePaperType(String paperType) {
        return isBlank(paperType) ? DEFAULT_PAPER_TYPE : paperType.trim();
    }

    private void ensureOfficialMetadata(String examName, Integer examYear, String sourceReference,
                                        Boolean sourceVerified) {
        int currentYear = Year.now().getValue();
        if (isBlank(examName) || examYear == null || examYear < 1900 || examYear > currentYear
                || isBlank(sourceReference)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "官方试卷发布前必须填写有效的考试名称、年份和来源");
        }
        if (!Boolean.TRUE.equals(sourceVerified)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "官方试卷发布前必须确认来源已核验");
        }
    }

    private void ensureOfficialQuestionNumbers(List<ExamQuestion> questions) {
        if (questions.isEmpty() || questions.stream().anyMatch(item -> isBlank(item.getDisplayNumber()))) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "官方试卷每道题必须填写展示题号");
        }
    }

    private void ensureQuestionRubric(Long questionId, int fullScore) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || !"SHORT_ANSWER".equals(question.getQuestionType())) { return; }
        List<SubjectiveGradingPoint> points = subjectiveGradingPointMapper.selectList(
                new LambdaQueryWrapper<SubjectiveGradingPoint>()
                        .eq(SubjectiveGradingPoint::getQuestionId, questionId));
        int rubricScore = points.stream().map(SubjectiveGradingPoint::getMaxScore)
                .filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        if (points.isEmpty() || rubricScore != fullScore) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "主观题发布前必须配置与题目分值一致的评分点");
        }
    }

    private List<ExamQuestion> findExamQuestions(Long paperId) {
        return examQuestionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, paperId));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
