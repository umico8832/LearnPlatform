package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.QuestionReviewRequest;
import com.learnplatform.dto.QuestionSubmissionRequest;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.QuestionSubmission;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionSubmissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 题目投稿服务
 */
@Service
public class QuestionSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionSubmissionService.class);

    private final QuestionSubmissionMapper submissionMapper;
    private final CourseMapper courseMapper;
    private final QuestionSubmissionOptionService optionService;
    private final QuestionSubmissionViewService viewService;

    public QuestionSubmissionService(QuestionSubmissionMapper submissionMapper,
                                     CourseMapper courseMapper,
                                     QuestionSubmissionOptionService optionService,
                                     QuestionSubmissionViewService viewService) {
        this.submissionMapper = submissionMapper;
        this.courseMapper = courseMapper;
        this.optionService = optionService;
        this.viewService = viewService;
    }

    /**
     * 用户提交题目投稿
     */
    @Transactional
    public QuestionSubmissionVO submitQuestion(QuestionSubmissionRequest request, Long userId) {
        // 校验课程存在
        Course course = courseMapper.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }

        // 校验题型合法
        String qt = request.getQuestionType();
        if (!isValidQuestionType(qt)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "不支持的题型: " + qt);
        }

        optionService.normalizeAndValidateRequest(request, qt);

        QuestionSubmission submission = new QuestionSubmission();
        submission.setUserId(userId);
        submission.setContent(request.getContent());
        submission.setQuestionType(request.getQuestionType());
        submission.setCourseId(request.getCourseId());
        submission.setDifficulty(request.getDifficulty());
        submission.setAnalysis(request.getAnalysis());
        submission.setOptionsJson(request.getOptionsJson());
        submission.setCorrectAnswer(request.getCorrectAnswer());
        submission.setKnowledgePointIds(request.getKnowledgePointIds());
        submission.setTags(request.getTags());
        submission.setSource(request.getSource());
        submission.setStatus(0); // 待审核
        submission.setDeleted(0);

        submissionMapper.insert(submission);
        log.info("用户 {} 提交题目投稿 {}, 题型: {}", userId, submission.getId(), submission.getQuestionType());

        return viewService.toView(submission);
    }

    /**
     * 用户查看自己的投稿列表
     */
    public Page<QuestionSubmissionVO> getMySubmissions(Long userId, int pageNum, int pageSize, Integer status) {
        Page<QuestionSubmission> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<QuestionSubmission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionSubmission::getUserId, userId);
        if (status != null) {
            wrapper.eq(QuestionSubmission::getStatus, status);
        }
        wrapper.orderByDesc(QuestionSubmission::getCreateTime);

        Page<QuestionSubmission> result = submissionMapper.selectPage(page, wrapper);
        return viewService.toPage(result);
    }

    /**
     * 管理端查看所有投稿（可按状态/课程筛选）
     */
    public Page<QuestionSubmissionVO> getAllSubmissions(int pageNum, int pageSize,
                                                        Integer status, Long courseId, String keyword) {
        Page<QuestionSubmission> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<QuestionSubmission> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(QuestionSubmission::getStatus, status);
        }
        if (courseId != null) {
            wrapper.eq(QuestionSubmission::getCourseId, courseId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(QuestionSubmission::getContent, keyword);
        }
        wrapper.orderByDesc(QuestionSubmission::getCreateTime);

        Page<QuestionSubmission> result = submissionMapper.selectPage(page, wrapper);
        return viewService.toPage(result);
    }

    /**
     * 查看投稿详情
     */
    public QuestionSubmissionVO getSubmissionById(Long id) {
        QuestionSubmission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿不存在");
        }
        return viewService.toView(submission);
    }

    /**
     * 管理员审核投稿
     */
    @Transactional
    public QuestionSubmissionVO reviewSubmission(Long submissionId, QuestionReviewRequest request, Long reviewerId) {
        QuestionSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿不存在");
        }
        if (submission.getStatus() != 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该投稿已审核，不能重复审核");
        }
        if (request.getStatus() != 1 && request.getStatus() != 2) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "审核状态只能为 1（通过）或 2（拒绝）");
        }

        submission.setStatus(request.getStatus());
        submission.setReviewComment(request.getReviewComment());
        submission.setReviewedBy(reviewerId);
        submission.setReviewedTime(LocalDateTime.now());
        submissionMapper.updateById(submission);

        log.info("管理员 {} 审核投稿 {}, 结果: {}", reviewerId, submissionId,
                request.getStatus() == 1 ? "通过" : "拒绝");

        return viewService.toView(submission);
    }

    /**
     * 投稿统计（管理端看板用）
     */
    public long countByStatus(int status) {
        LambdaQueryWrapper<QuestionSubmission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionSubmission::getStatus, status);
        return submissionMapper.selectCount(wrapper);
    }

    /**
     * 更新投稿的知识点关联（管理端 AI 标注应用）
     */
    public QuestionSubmissionVO updateKnowledgePointIds(Long submissionId, String knowledgePointIds) {
        QuestionSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿不存在");
        }
        submission.setKnowledgePointIds(knowledgePointIds);
        submissionMapper.updateById(submission);
        log.info("更新投稿 {} 知识点为: {}", submissionId, knowledgePointIds);
        return viewService.toView(submission);
    }

    private boolean isValidQuestionType(String type) {
        return Arrays.asList("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE",
                "FILL_BLANK", "SHORT_ANSWER").contains(type);
    }

}
