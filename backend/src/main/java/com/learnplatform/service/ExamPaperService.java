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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 试卷服务（管理端）
 */
@Service
public class ExamPaperService {

    private static final Logger log = LoggerFactory.getLogger(ExamPaperService.class);

    private final ExamPaperMapper examPaperMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final CourseMapper courseMapper;

    public ExamPaperService(ExamPaperMapper examPaperMapper,
                            ExamQuestionMapper examQuestionMapper,
                            QuestionMapper questionMapper,
                            QuestionOptionMapper questionOptionMapper,
                            CourseMapper courseMapper) {
        this.examPaperMapper = examPaperMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 分页查询试卷
     */
    public Page<ExamPaperVO> getExamPaperPage(int pageNum, int pageSize, Long courseId, Integer status) {
        Page<ExamPaper> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExamPaper> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) wrapper.eq(ExamPaper::getCourseId, courseId);
        if (status != null) wrapper.eq(ExamPaper::getStatus, status);
        wrapper.orderByDesc(ExamPaper::getCreateTime);
        Page<ExamPaper> result = examPaperMapper.selectPage(page, wrapper);

        Page<ExamPaperVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 获取试卷详情
     */
    public ExamPaperVO getExamPaperById(Long id) {
        ExamPaper paper = examPaperMapper.selectById(id);
        if (paper == null) throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        ExamPaperVO vo = toVO(paper);
        fillQuestions(vo);
        return vo;
    }

    /**
     * 创建试卷（含组卷）
     */
    @Transactional
    public ExamPaperVO createExamPaper(ExamPaperCreateRequest request, Long createBy) {
        ExamPaper paper = new ExamPaper();
        paper.setTitle(request.getTitle());
        paper.setDescription(request.getDescription());
        paper.setCourseId(request.getCourseId());
        paper.setDuration(request.getDuration() != null ? request.getDuration() : 60);
        paper.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        paper.setCreateBy(createBy);
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
        ExamPaper paper = examPaperMapper.selectById(id);
        if (paper == null) throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");

        if (request.getTitle() != null) paper.setTitle(request.getTitle());
        if (request.getDescription() != null) paper.setDescription(request.getDescription());
        if (request.getCourseId() != null) paper.setCourseId(request.getCourseId());
        if (request.getDuration() != null) paper.setDuration(request.getDuration());
        if (request.getStatus() != null) paper.setStatus(request.getStatus());
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
        ExamPaper paper = examPaperMapper.selectById(id);
        if (paper == null) throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        examPaperMapper.deleteById(id);
        LambdaQueryWrapper<ExamQuestion> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ExamQuestion::getExamPaperId, id);
        examQuestionMapper.delete(deleteWrapper);
    }

    /**
     * 发布试卷
     */
    public void publishExamPaper(Long id) {
        ExamPaper paper = examPaperMapper.selectById(id);
        if (paper == null) throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        paper.setStatus(1);
        examPaperMapper.updateById(paper);
    }

    // ======================== 私有方法 ========================

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
        vo.setCreateTime(paper.getCreateTime());
        if (paper.getCourseId() != null) {
            Course course = courseMapper.selectById(paper.getCourseId());
            if (course != null) vo.setCourseName(course.getName());
        }
        return vo;
    }

    private void fillQuestions(ExamPaperVO vo) {
        LambdaQueryWrapper<ExamQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamQuestion::getExamPaperId, vo.getId()).orderByAsc(ExamQuestion::getSortOrder);
        List<ExamQuestion> eqs = examQuestionMapper.selectList(wrapper);

        List<ExamPaperVO.ExamQuestionItem> items = new ArrayList<>();
        for (ExamQuestion eq : eqs) {
            ExamPaperVO.ExamQuestionItem item = new ExamPaperVO.ExamQuestionItem();
            item.setQuestionId(eq.getQuestionId());
            item.setSortOrder(eq.getSortOrder());
            item.setScore(eq.getScore());

            Question q = questionMapper.selectById(eq.getQuestionId());
            if (q != null) {
                item.setContent(q.getContent());
                item.setQuestionType(q.getQuestionType());

                LambdaQueryWrapper<QuestionOption> optWrapper = new LambdaQueryWrapper<>();
                optWrapper.eq(QuestionOption::getQuestionId, q.getId()).orderByAsc(QuestionOption::getSortOrder);
                List<QuestionOption> options = questionOptionMapper.selectList(optWrapper);
                item.setOptions(options.stream().map(QuestionOptionVO::fromEntity).collect(Collectors.toList()));
            }
            items.add(item);
        }
        vo.setQuestions(items);
    }
}