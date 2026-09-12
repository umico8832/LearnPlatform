package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionDuplicateGroupVO;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.service.question.QuestionDuplicateDetector;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionService {
    private final QuestionMapper questionMapper;
    private final QuestionViewService viewService;
    private final QuestionMutationService mutationService;

    public QuestionService(
            QuestionMapper questionMapper,
            QuestionViewService viewService,
            QuestionMutationService mutationService) {
        this.questionMapper = questionMapper;
        this.viewService = viewService;
        this.mutationService = mutationService;
    }

    public Page<QuestionVO> getQuestionPage(int pageNum, int pageSize, String keyword,
                                             String questionType, Long courseId,
                                             Integer difficulty, Integer status) {
        return getQuestionPage(pageNum, pageSize, keyword, questionType, courseId, difficulty, status, null);
    }

    public Page<QuestionVO> getQuestionPage(int pageNum, int pageSize, String keyword,
                                             String questionType, Long courseId,
                                             Integer difficulty, Integer status, String sourceType) {
        Page<Question> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getVisibility, "PUBLIC");
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Question::getContent, keyword);
        }
        if (questionType != null && !questionType.isEmpty()) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        if (courseId != null) {
            wrapper.eq(Question::getCourseId, courseId);
        }
        if (difficulty != null) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }
        if (status != null) {
            wrapper.eq(Question::getStatus, status);
        }
        if (sourceType != null && !sourceType.isEmpty()) {
            wrapper.eq(Question::getSourceType, sourceType);
        }
        wrapper.orderByDesc(Question::getCreateTime);
        Page<Question> result = questionMapper.selectPage(page, wrapper);
        Page<QuestionVO> views = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        views.setRecords(result.getRecords().stream().map(this::toAdminView).toList());
        return views;
    }

    public Page<QuestionVO> getEnabledQuestionPage(int pageNum, int pageSize, String questionType,
                                                    Long courseId, Integer difficulty) {
        Page<Question> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getStatus, 1).eq(Question::getVisibility, "PUBLIC");
        if (questionType != null && !questionType.isEmpty()) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        if (courseId != null) {
            wrapper.eq(Question::getCourseId, courseId);
        }
        if (difficulty != null) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }
        wrapper.orderByDesc(Question::getCreateTime);
        Page<Question> result = questionMapper.selectPage(page, wrapper);
        Page<QuestionVO> views = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        views.setRecords(result.getRecords().stream().map(this::toUserView).toList());
        return views;
    }

    public QuestionVO getQuestionById(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null || "PRIVATE".equals(question.getVisibility())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
        return toAdminView(question);
    }

    public QuestionVO getEnabledQuestionById(Long id, Long userId) {
        Question question = questionMapper.selectById(id);
        boolean accessible = question != null && (question.getVisibility() == null
                || "PUBLIC".equals(question.getVisibility())
                || ("PRIVATE".equals(question.getVisibility()) && userId.equals(question.getOwnerUserId())));
        if (!accessible || question.getStatus() == null || question.getStatus() != 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
        return toUserView(question);
    }

    public List<QuestionDuplicateGroupVO> findDuplicateGroups(
            Long courseId, String questionType, Integer minSimilarity, Integer limit) {
        int threshold = minSimilarity != null ? Math.max(70, Math.min(100, minSimilarity)) : 92;
        int maxGroups = limit != null ? Math.max(1, Math.min(50, limit)) : 20;
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getVisibility, "PUBLIC");
        if (courseId != null) {
            wrapper.eq(Question::getCourseId, courseId);
        }
        if (questionType != null && !questionType.isBlank()) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        wrapper.orderByDesc(Question::getCreateTime);
        return QuestionDuplicateDetector.detect(questionMapper.selectList(wrapper), threshold).stream()
                .map(viewService::toDuplicateGroup)
                .limit(maxGroups)
                .toList();
    }

    @Transactional
    public QuestionVO createQuestion(QuestionCreateRequest request, Long createBy) {
        Long id = mutationService.create(request, createBy, "MANUAL", null, null);
        return getQuestionById(id);
    }

    @Transactional
    public QuestionVO createReviewedAiQuestion(
            QuestionCreateRequest request, Long createBy,
            String sourceReference, Long originQuestionId) {
        Long id = mutationService.create(
                request, createBy, "AI_GENERATED", sourceReference, originQuestionId);
        return getQuestionById(id);
    }

    @CacheEvict(value = "questionReviewSuggestion", key = "#id")
    @Transactional
    public QuestionVO updateQuestion(Long id, QuestionCreateRequest request, Long operatorId) {
        mutationService.update(id, request, operatorId);
        return getQuestionById(id);
    }

    @CacheEvict(value = "questionReviewSuggestion", key = "#id")
    @Transactional
    public void deleteQuestion(Long id, Long operatorId) {
        mutationService.delete(id, operatorId);
    }

    private QuestionVO toAdminView(Question question) {
        QuestionVO view = QuestionVO.fromEntity(question);
        viewService.enrich(view);
        return view;
    }

    private QuestionVO toUserView(Question question) {
        QuestionVO view = QuestionVO.fromEntity(question);
        view.setAnalysis(null);
        viewService.enrichForUser(view);
        return view;
    }
}
