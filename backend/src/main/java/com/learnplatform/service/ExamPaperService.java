package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamPaperCreateRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.QuestionOptionVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 试卷服务（管理端）
 */
@Service
public class ExamPaperService {

    private static final Logger log = LoggerFactory.getLogger(ExamPaperService.class);
    private static final String DEFAULT_PAPER_TYPE = "PRACTICE";
    private static final String OFFICIAL_PAPER_TYPE = "OFFICIAL_EXAM";
    private static final Set<String> PAPER_TYPES = Set.of(DEFAULT_PAPER_TYPE, OFFICIAL_PAPER_TYPE);

    private final ExamPaperMapper examPaperMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final CourseMapper courseMapper;
    private final SubjectiveGradingPointMapper subjectiveGradingPointMapper;

    public ExamPaperService(ExamPaperMapper examPaperMapper,
                            ExamQuestionMapper examQuestionMapper,
                            QuestionMapper questionMapper,
                            QuestionOptionMapper questionOptionMapper,
                            CourseMapper courseMapper,
                            SubjectiveGradingPointMapper subjectiveGradingPointMapper) {
        this.examPaperMapper = examPaperMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.courseMapper = courseMapper;
        this.subjectiveGradingPointMapper = subjectiveGradingPointMapper;
    }

    /**
     * 分页查询试卷
     */
    public Page<ExamPaperVO> getExamPaperPage(int pageNum, int pageSize, Long courseId, Integer status) {
        Page<ExamPaper> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExamPaper> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) wrapper.eq(ExamPaper::getCourseId, courseId);
        if (status != null) wrapper.eq(ExamPaper::getStatus, status);
        wrapper.eq(ExamPaper::getVisibility, "PUBLIC");
        wrapper.orderByDesc(ExamPaper::getCreateTime);
        Page<ExamPaper> result = examPaperMapper.selectPage(page, wrapper);

        Page<ExamPaperVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    public Page<ExamPaperVO> getAccessiblePublishedExamPaperPage(Long userId, int pageNum,
                                                                 int pageSize, Long courseId) {
        Page<ExamPaper> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExamPaper> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamPaper::getStatus, 1)
                .and(scope -> scope.eq(ExamPaper::getVisibility, "PUBLIC")
                        .or(privateScope -> privateScope.eq(ExamPaper::getVisibility, "PRIVATE")
                                .eq(ExamPaper::getOwnerUserId, userId)));
        if (courseId != null) wrapper.eq(ExamPaper::getCourseId, courseId);
        wrapper.orderByDesc(ExamPaper::getCreateTime);
        Page<ExamPaper> result = examPaperMapper.selectPage(page, wrapper);
        Page<ExamPaperVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 获取试卷详情
     */
    public ExamPaperVO getExamPaperById(Long id) {
        ExamPaper paper = examPaperMapper.selectById(id);
        if (paper == null || "PRIVATE".equals(paper.getVisibility())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        ExamPaperVO vo = toVO(paper);
        fillQuestions(vo, true);
        return vo;
    }

    public ExamPaperVO getAccessiblePublishedExamPaperById(Long id, Long userId) {
        ExamPaper paper = examPaperMapper.selectById(id);
        if (paper == null || paper.getStatus() == null || paper.getStatus() != 1
                || !canAccess(paper, userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        ExamPaperVO vo = toVO(paper);
        fillQuestions(vo, false);
        return vo;
    }

    public boolean canAccess(ExamPaper paper, Long userId) {
        String visibility = paper.getVisibility();
        if (visibility == null || "PUBLIC".equals(visibility)) return true;
        return "PRIVATE".equals(visibility) && userId != null && userId.equals(paper.getOwnerUserId());
    }

    /**
     * 创建试卷（含组卷）
     */
    @Transactional
    public ExamPaperVO createExamPaper(ExamPaperCreateRequest request, Long createBy) {
        String paperType = normalizePaperType(request.getPaperType());
        ensurePaperTypeSupported(paperType);
        ensurePublishable(request.getStatus(), request.getQuestions(), 0);
        ensureOfficialRequestReady(request.getStatus(), paperType, request.getExamName(),
                request.getExamYear(), request.getSourceReference(), request.getSourceVerified(),
                request.getQuestions(), null);
        ensureManualGradingReady(request.getStatus(), request.getQuestions(), null);

        ExamPaper paper = new ExamPaper();
        paper.setTitle(request.getTitle());
        paper.setDescription(request.getDescription());
        paper.setCourseId(request.getCourseId());
        paper.setDuration(request.getDuration() != null ? request.getDuration() : 60);
        paper.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        paper.setCreateBy(createBy);
        paper.setVisibility("PUBLIC");
        paper.setPaperType(paperType);
        paper.setExamName(request.getExamName());
        paper.setExamYear(request.getExamYear());
        paper.setSourceReference(request.getSourceReference());
        paper.setSourceVerified(Boolean.TRUE.equals(request.getSourceVerified()));
        paper.setDeleted(0);

        int totalScore = 0;
        int questionCount = 0;

        if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            questionCount = request.getQuestions().size();
            for (ExamPaperCreateRequest.QuestionItem item : request.getQuestions()) {
                totalScore += (item.getScore() != null ? item.getScore() : 1);
            }
        }

        paper.setTotalScore(totalScore);
        paper.setQuestionCount(questionCount);
        examPaperMapper.insert(paper);

        // 保存试卷-题目关联
        if (request.getQuestions() != null) {
            for (ExamPaperCreateRequest.QuestionItem item : request.getQuestions()) {
                ExamQuestion eq = new ExamQuestion();
                eq.setExamPaperId(paper.getId());
                eq.setQuestionId(item.getQuestionId());
                eq.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                eq.setScore(item.getScore() != null ? item.getScore() : 1);
                applyQuestionStructure(eq, item);
                examQuestionMapper.insert(eq);
            }
        }

        return getExamPaperById(paper.getId());
    }

    /**
     * 更新试卷
     */
    @Transactional
    public ExamPaperVO updateExamPaper(Long id, ExamPaperCreateRequest request) {
        ExamPaper paper = examPaperMapper.selectByIdForUpdate(id);
        if (paper == null) throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        ensureDraft(paper, "已发布试卷不能修改");
        String paperType = request.getPaperType() != null
                ? normalizePaperType(request.getPaperType()) : normalizePaperType(paper.getPaperType());
        ensurePaperTypeSupported(paperType);
        ensurePublishable(request.getStatus(), request.getQuestions(), paper.getQuestionCount());

        String examName = request.getExamName() != null ? request.getExamName() : paper.getExamName();
        Integer examYear = request.getExamYear() != null ? request.getExamYear() : paper.getExamYear();
        String sourceReference = request.getSourceReference() != null
                ? request.getSourceReference() : paper.getSourceReference();
        Boolean sourceVerified = request.getSourceVerified() != null
                ? request.getSourceVerified() : paper.getSourceVerified();
        Integer status = request.getStatus() != null ? request.getStatus() : paper.getStatus();
        ensureOfficialRequestReady(status, paperType, examName, examYear, sourceReference,
                sourceVerified, request.getQuestions(), id);
        ensureManualGradingReady(status, request.getQuestions(), id);

        if (request.getTitle() != null) paper.setTitle(request.getTitle());
        if (request.getDescription() != null) paper.setDescription(request.getDescription());
        if (request.getCourseId() != null) paper.setCourseId(request.getCourseId());
        if (request.getDuration() != null) paper.setDuration(request.getDuration());
        if (request.getStatus() != null) paper.setStatus(request.getStatus());
        paper.setPaperType(paperType);
        if (request.getExamName() != null) paper.setExamName(request.getExamName());
        if (request.getExamYear() != null) paper.setExamYear(request.getExamYear());
        if (request.getSourceReference() != null) paper.setSourceReference(request.getSourceReference());
        if (request.getSourceVerified() != null) paper.setSourceVerified(request.getSourceVerified());
        examPaperMapper.updateById(paper);

        // 更新题目关联
        if (request.getQuestions() != null) {
            LambdaQueryWrapper<ExamQuestion> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(ExamQuestion::getExamPaperId, id);
            examQuestionMapper.delete(deleteWrapper);

            int totalScore = 0;
            for (ExamPaperCreateRequest.QuestionItem item : request.getQuestions()) {
                ExamQuestion eq = new ExamQuestion();
                eq.setExamPaperId(id);
                eq.setQuestionId(item.getQuestionId());
                eq.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                eq.setScore(item.getScore() != null ? item.getScore() : 1);
                applyQuestionStructure(eq, item);
                examQuestionMapper.insert(eq);
                totalScore += (item.getScore() != null ? item.getScore() : 1);
            }
            paper.setTotalScore(totalScore);
            paper.setQuestionCount(request.getQuestions().size());
            examPaperMapper.updateById(paper);
        }

        return getExamPaperById(id);
    }

    /**
     * 删除试卷
     */
    @Transactional
    public void deleteExamPaper(Long id) {
        ExamPaper paper = examPaperMapper.selectByIdForUpdate(id);
        if (paper == null) throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        ensureDraft(paper, "已发布试卷不能删除");
        examPaperMapper.deleteById(id);
        LambdaQueryWrapper<ExamQuestion> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ExamQuestion::getExamPaperId, id);
        examQuestionMapper.delete(deleteWrapper);
    }

    /**
     * 发布试卷
     */
    @Transactional
    public void publishExamPaper(Long id) {
        ExamPaper paper = examPaperMapper.selectByIdForUpdate(id);
        if (paper == null) throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        if (paper.getQuestionCount() == null || paper.getQuestionCount() <= 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "空试卷不能发布");
        }
        ensureOfficialPaperReady(paper);
        ensureManualGradingReady(1, null, paper.getId());
        paper.setStatus(1);
        examPaperMapper.updateById(paper);
    }

    // ======================== 私有方法 ========================

    private void ensureDraft(ExamPaper paper, String message) {
        if (paper.getStatus() != null && paper.getStatus() == 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, message);
        }
    }

    private void ensurePublishable(Integer requestedStatus,
                                   List<ExamPaperCreateRequest.QuestionItem> questions,
                                   Integer currentQuestionCount) {
        if (requestedStatus == null || requestedStatus != 1) {
            return;
        }
        int questionCount = questions != null
                ? questions.size()
                : (currentQuestionCount != null ? currentQuestionCount : 0);
        if (questionCount <= 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "空试卷不能发布");
        }
    }

    private void ensureOfficialRequestReady(Integer status, String paperType, String examName,
                                            Integer examYear, String sourceReference, Boolean sourceVerified,
                                            List<ExamPaperCreateRequest.QuestionItem> questions, Long paperId) {
        if (status == null || status != 1 || !OFFICIAL_PAPER_TYPE.equals(paperType)) {
            return;
        }
        ensureOfficialMetadata(examName, examYear, sourceReference, sourceVerified);
        if (questions != null) {
            if (questions.stream().anyMatch(item -> item == null || isBlank(item.getDisplayNumber()))) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "官方试卷每道题必须填写展示题号");
            }
            return;
        }
        if (paperId != null) {
            ensureOfficialQuestionNumbers(findExamQuestions(paperId));
        }
    }

    private void ensureOfficialPaperReady(ExamPaper paper) {
        String paperType = normalizePaperType(paper.getPaperType());
        ensurePaperTypeSupported(paperType);
        if (!OFFICIAL_PAPER_TYPE.equals(paperType)) {
            return;
        }
        ensureOfficialMetadata(paper.getExamName(), paper.getExamYear(), paper.getSourceReference(),
                paper.getSourceVerified());
        ensureOfficialQuestionNumbers(findExamQuestions(paper.getId()));
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

    private void ensureManualGradingReady(Integer status,
                                          List<ExamPaperCreateRequest.QuestionItem> requestedQuestions,
                                          Long paperId) {
        if (status == null || status != 1) {
            return;
        }
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

    private void ensureQuestionRubric(Long questionId, int fullScore) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || !"SHORT_ANSWER".equals(question.getQuestionType())) {
            return;
        }
        List<SubjectiveGradingPoint> points = subjectiveGradingPointMapper.selectList(
                new LambdaQueryWrapper<SubjectiveGradingPoint>()
                        .eq(SubjectiveGradingPoint::getQuestionId, questionId));
        int rubricScore = points.stream()
                .map(SubjectiveGradingPoint::getMaxScore)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        if (points.isEmpty() || rubricScore != fullScore) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "主观题发布前必须配置与题目分值一致的评分点");
        }
    }

    private List<ExamQuestion> findExamQuestions(Long paperId) {
        return examQuestionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, paperId));
    }

    private String normalizePaperType(String paperType) {
        return isBlank(paperType) ? DEFAULT_PAPER_TYPE : paperType.trim();
    }

    private void ensurePaperTypeSupported(String paperType) {
        if (!PAPER_TYPES.contains(paperType)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "不支持的试卷类型");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void applyQuestionStructure(ExamQuestion examQuestion, ExamPaperCreateRequest.QuestionItem item) {
        examQuestion.setSectionTitle(item.getSectionTitle());
        examQuestion.setMajorQuestionNumber(item.getMajorQuestionNumber());
        examQuestion.setMinorQuestionNumber(item.getMinorQuestionNumber());
        examQuestion.setSubquestionNumber(item.getSubquestionNumber());
        examQuestion.setDisplayNumber(item.getDisplayNumber());
    }

    private ExamPaperVO toVO(ExamPaper paper) {
        ExamPaperVO vo = new ExamPaperVO();
        vo.setId(paper.getId());
        vo.setTitle(paper.getTitle());
        vo.setDescription(paper.getDescription());
        vo.setCourseId(paper.getCourseId());
        vo.setTotalScore(paper.getTotalScore());
        vo.setDuration(paper.getDuration());
        vo.setQuestionCount(paper.getQuestionCount());
        vo.setStatus(paper.getStatus());
        vo.setCreateBy(paper.getCreateBy());
        vo.setOwnerUserId(paper.getOwnerUserId());
        vo.setVisibility(paper.getVisibility() != null ? paper.getVisibility() : "PUBLIC");
        vo.setPaperType(normalizePaperType(paper.getPaperType()));
        vo.setExamName(paper.getExamName());
        vo.setExamYear(paper.getExamYear());
        vo.setSourceReference(paper.getSourceReference());
        vo.setSourceVerified(Boolean.TRUE.equals(paper.getSourceVerified()));
        vo.setImportStatus(paper.getImportStatus());
        vo.setCreateTime(paper.getCreateTime());
        if (paper.getCourseId() != null) {
            Course course = courseMapper.selectById(paper.getCourseId());
            if (course != null) vo.setCourseName(course.getName());
        }
        return vo;
    }

    private void fillQuestions(ExamPaperVO vo, boolean includeCorrectAnswer) {
        LambdaQueryWrapper<ExamQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamQuestion::getExamPaperId, vo.getId()).orderByAsc(ExamQuestion::getSortOrder);
        List<ExamQuestion> eqs = examQuestionMapper.selectList(wrapper);

        List<ExamPaperVO.ExamQuestionItem> items = new ArrayList<>();
        for (ExamQuestion eq : eqs) {
            ExamPaperVO.ExamQuestionItem item = new ExamPaperVO.ExamQuestionItem();
            item.setQuestionId(eq.getQuestionId());
            item.setSortOrder(eq.getSortOrder());
            item.setScore(eq.getScore());
            item.setSectionTitle(eq.getSectionTitle());
            item.setMajorQuestionNumber(eq.getMajorQuestionNumber());
            item.setMinorQuestionNumber(eq.getMinorQuestionNumber());
            item.setSubquestionNumber(eq.getSubquestionNumber());
            item.setDisplayNumber(eq.getDisplayNumber());

            Question q = questionMapper.selectById(eq.getQuestionId());
            if (q != null) {
                item.setContent(q.getContent());
                item.setQuestionType(q.getQuestionType());

                LambdaQueryWrapper<QuestionOption> optWrapper = new LambdaQueryWrapper<>();
                optWrapper.eq(QuestionOption::getQuestionId, q.getId()).orderByAsc(QuestionOption::getSortOrder);
                List<QuestionOption> options = questionOptionMapper.selectList(optWrapper);
                item.setOptions(options.stream().map(option -> {
                    QuestionOptionVO optionVO = QuestionOptionVO.fromEntity(option);
                    if (!includeCorrectAnswer) {
                        optionVO.setIsCorrect(null);
                    }
                    return optionVO;
                }).collect(Collectors.toList()));
            }
            items.add(item);
        }
        vo.setQuestions(items);
    }
}
