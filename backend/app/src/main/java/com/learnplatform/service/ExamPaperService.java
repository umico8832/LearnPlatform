package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamPaperCreateRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 试卷服务（管理端）
 */
@Service
public class ExamPaperService {

    private static final Logger log = LoggerFactory.getLogger(ExamPaperService.class);
    private final ExamPaperMapper examPaperMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final ExamPaperViewService viewService;
    private final ExamPaperValidationService validationService;

    public ExamPaperService(ExamPaperMapper examPaperMapper,
                            ExamQuestionMapper examQuestionMapper,
                            ExamPaperViewService viewService,
                            ExamPaperValidationService validationService) {
        this.examPaperMapper = examPaperMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.viewService = viewService;
        this.validationService = validationService;
    }

    /**
     * 分页查询试卷
     */
    public Page<ExamPaperVO> getExamPaperPage(int pageNum, int pageSize, Long courseId, Integer status) {
        return viewService.getPublicPage(pageNum, pageSize, courseId, status);
    }

    public Page<ExamPaperVO> getAccessiblePublishedExamPaperPage(Long userId, int pageNum,
                                                                 int pageSize, Long courseId) {
        return viewService.getAccessiblePublishedPage(userId, pageNum, pageSize, courseId);
    }

    /**
     * 获取试卷详情
     */
    public ExamPaperVO getExamPaperById(Long id) {
        return viewService.getPublicById(id);
    }

    public ExamPaperVO getAccessiblePublishedExamPaperById(Long id, Long userId) {
        return viewService.getAccessiblePublishedById(id, userId);
    }

    public boolean canAccess(ExamPaper paper, Long userId) {
        return viewService.canAccess(paper, userId);
    }

    /**
     * 创建试卷（含组卷）
     */
    @Transactional
    public ExamPaperVO createExamPaper(ExamPaperCreateRequest request, Long createBy) {
        String paperType = ExamPaperValidationService.normalizePaperType(request.getPaperType());
        validationService.ensurePaperTypeSupported(paperType);
        validationService.ensurePublishable(request.getStatus(), request.getQuestions(), 0);
        validationService.ensureOfficialRequestReady(request.getStatus(), paperType, request.getExamName(),
                request.getExamYear(), request.getSourceReference(), request.getSourceVerified(),
                request.getQuestions(), null);
        validationService.ensureManualGradingReady(request.getStatus(), request.getQuestions(), null);

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
        if (paper == null) { throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在"); }
        validationService.ensureDraft(paper, "已发布试卷不能修改");
        String paperType = request.getPaperType() != null
                ? ExamPaperValidationService.normalizePaperType(request.getPaperType())
                : ExamPaperValidationService.normalizePaperType(paper.getPaperType());
        validationService.ensurePaperTypeSupported(paperType);
        validationService.ensurePublishable(request.getStatus(), request.getQuestions(), paper.getQuestionCount());

        String examName = request.getExamName() != null ? request.getExamName() : paper.getExamName();
        Integer examYear = request.getExamYear() != null ? request.getExamYear() : paper.getExamYear();
        String sourceReference = request.getSourceReference() != null
                ? request.getSourceReference() : paper.getSourceReference();
        Boolean sourceVerified = request.getSourceVerified() != null
                ? request.getSourceVerified() : paper.getSourceVerified();
        Integer status = request.getStatus() != null ? request.getStatus() : paper.getStatus();
        validationService.ensureOfficialRequestReady(status, paperType, examName, examYear, sourceReference,
                sourceVerified, request.getQuestions(), id);
        validationService.ensureManualGradingReady(status, request.getQuestions(), id);

        if (request.getTitle() != null) { paper.setTitle(request.getTitle()); }
        if (request.getDescription() != null) { paper.setDescription(request.getDescription()); }
        if (request.getCourseId() != null) { paper.setCourseId(request.getCourseId()); }
        if (request.getDuration() != null) { paper.setDuration(request.getDuration()); }
        if (request.getStatus() != null) { paper.setStatus(request.getStatus()); }
        paper.setPaperType(paperType);
        if (request.getExamName() != null) { paper.setExamName(request.getExamName()); }
        if (request.getExamYear() != null) { paper.setExamYear(request.getExamYear()); }
        if (request.getSourceReference() != null) { paper.setSourceReference(request.getSourceReference()); }
        if (request.getSourceVerified() != null) { paper.setSourceVerified(request.getSourceVerified()); }
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
        if (paper == null) { throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在"); }
        validationService.ensureDraft(paper, "已发布试卷不能删除");
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
        if (paper == null) { throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在"); }
        if (paper.getQuestionCount() == null || paper.getQuestionCount() <= 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "空试卷不能发布");
        }
        validationService.ensureOfficialPaperReady(paper);
        validationService.ensureManualGradingReady(1, null, paper.getId());
        paper.setStatus(1);
        examPaperMapper.updateById(paper);
    }

    private void applyQuestionStructure(ExamQuestion examQuestion, ExamPaperCreateRequest.QuestionItem item) {
        examQuestion.setSectionTitle(item.getSectionTitle());
        examQuestion.setMajorQuestionNumber(item.getMajorQuestionNumber());
        examQuestion.setMinorQuestionNumber(item.getMinorQuestionNumber());
        examQuestion.setSubquestionNumber(item.getSubquestionNumber());
        examQuestion.setDisplayNumber(item.getDisplayNumber());
    }

}
