package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionDuplicateGroupVO;
import com.learnplatform.dto.QuestionOptionVO;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.service.question.QuestionDuplicateDetector;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目服务
 */
@Service
public class QuestionService {

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionVersionService questionVersionService;

    public QuestionService(QuestionMapper questionMapper,
                           QuestionOptionMapper questionOptionMapper,
                           QuestionKnowledgePointMapper questionKnowledgePointMapper,
                           CourseMapper courseMapper,
                           KnowledgePointMapper knowledgePointMapper,
                           ExamQuestionMapper examQuestionMapper,
                           QuestionVersionService questionVersionService) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionVersionService = questionVersionService;
    }

    /**
     * 分页查询题目（管理端，包含所有状态）— 无 sourceType 的向后兼容重载
     */
    public Page<QuestionVO> getQuestionPage(int pageNum, int pageSize, String keyword,
                                             String questionType, Long courseId,
                                             Integer difficulty, Integer status) {
        return getQuestionPage(pageNum, pageSize, keyword, questionType, courseId, difficulty, status, null);
    }

    /**
     * 分页查询题目（管理端，包含所有状态，支持来源筛选）
     */
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

        Page<QuestionVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(q -> {
                    QuestionVO vo = QuestionVO.fromEntity(q);
                    fillQuestionVO(vo);
                    return vo;
                })
                .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 分页查询启用的题目（用户端）
     */
    public Page<QuestionVO> getEnabledQuestionPage(int pageNum, int pageSize, String questionType,
                                                    Long courseId, Integer difficulty) {
        Page<Question> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getStatus, 1);
        wrapper.eq(Question::getVisibility, "PUBLIC");
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

        Page<QuestionVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(q -> {
                    QuestionVO vo = QuestionVO.fromEntity(q);
                    vo.setAnalysis(null);
                    fillQuestionVOForUser(vo);
                    return vo;
                })
                .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 获取题目详情
     */
    public QuestionVO getQuestionById(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null || "PRIVATE".equals(question.getVisibility())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
        QuestionVO vo = QuestionVO.fromEntity(question);
        fillQuestionVO(vo);
        return vo;
    }

    public QuestionVO getEnabledQuestionById(Long id, Long userId) {
        Question question = questionMapper.selectById(id);
        boolean accessible = question != null && (question.getVisibility() == null
                || "PUBLIC".equals(question.getVisibility())
                || ("PRIVATE".equals(question.getVisibility()) && userId.equals(question.getOwnerUserId())));
        if (!accessible || question.getStatus() == null || question.getStatus() != 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
        QuestionVO vo = QuestionVO.fromEntity(question);
        vo.setAnalysis(null);
        fillQuestionVOForUser(vo);
        return vo;
    }

    /**
     * 检测管理端题库中的疑似重复题目。
     */
    public List<QuestionDuplicateGroupVO> findDuplicateGroups(Long courseId, String questionType,
                                                              Integer minSimilarity, Integer limit) {
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
                .map(this::buildDuplicateGroup)
                .limit(maxGroups)
                .collect(Collectors.toList());
    }

    /**
     * 创建题目（包含选项和知识点关联）
     */
    @Transactional
    public QuestionVO createQuestion(QuestionCreateRequest request, Long createBy) {
        return createQuestion(request, createBy, "MANUAL", null, null);
    }

    /** 将已审查的 AI 变式题创建为正式题目，并在首个版本快照中保留来源链路。 */
    @Transactional
    public QuestionVO createReviewedAiQuestion(QuestionCreateRequest request, Long createBy,
                                                String sourceReference, Long originQuestionId) {
        return createQuestion(request, createBy, "AI_GENERATED", sourceReference, originQuestionId);
    }

    private QuestionVO createQuestion(QuestionCreateRequest request, Long createBy,
                                      String sourceType, String sourceReference, Long originQuestionId) {
        // 校验课程是否存在
        Course course = courseMapper.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }

        // 创建题目
        Question question = new Question();
        question.setContent(request.getContent());
        question.setQuestionType(request.getQuestionType());
        question.setCourseId(request.getCourseId());
        question.setDifficulty(request.getDifficulty() != null ? request.getDifficulty() : 3);
        question.setAnalysis(request.getAnalysis());
        question.setTags(request.getTags());
        question.setScore(request.getScore() != null ? request.getScore() : 1);
        question.setStatus(1);
        question.setCreateBy(createBy);
        question.setVisibility("PUBLIC");
        question.setSourceType(sourceType);
        question.setSourceReference(sourceReference);
        question.setOriginQuestionId(originQuestionId);
        question.setReviewRounds(0);
        question.setNextReviewTime(java.time.LocalDateTime.now().plusDays(90));
        question.setDeleted(0);
        questionMapper.insert(question);

        // 保存选项
        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            for (QuestionCreateRequest.OptionItem item : request.getOptions()) {
                QuestionOption option = new QuestionOption();
                option.setQuestionId(question.getId());
                option.setContent(item.getContent());
                option.setOptionLabel(item.getOptionLabel());
                option.setIsCorrect(item.getIsCorrect() != null ? item.getIsCorrect() : 0);
                option.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                option.setDeleted(0);
                questionOptionMapper.insert(option);
            }
        }

        // 保存知识点关联
        if (request.getKnowledgePointIds() != null && !request.getKnowledgePointIds().isEmpty()) {
            for (Long kpId : request.getKnowledgePointIds()) {
                KnowledgePoint kp = knowledgePointMapper.selectById(kpId);
                if (kp == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "知识点不存在: " + kpId);
                }
                QuestionKnowledgePoint qkp = new QuestionKnowledgePoint();
                qkp.setQuestionId(question.getId());
                qkp.setKnowledgePointId(kpId);
                questionKnowledgePointMapper.insert(qkp);
            }
        }

        questionVersionService.recordChange(question.getId(), "CREATE", createBy,
                "创建题目", null, questionMapper.selectById(question.getId()));
        return getQuestionById(question.getId());
    }

    /**
     * 更新题目
     */
    @CacheEvict(value = "questionReviewSuggestion", key = "#id")
    @Transactional
    public QuestionVO updateQuestion(Long id, QuestionCreateRequest request, Long operatorId) {
        Question question = questionMapper.selectById(id);
        if (question == null || "PRIVATE".equals(question.getVisibility())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
        ensureNotUsedByPublishedPaper(id);
        String snapshotBefore = questionVersionService.buildSnapshotJson(question);

        // 更新题目基本信息
        if (request.getContent() != null) { question.setContent(request.getContent()); }
        if (request.getQuestionType() != null) { question.setQuestionType(request.getQuestionType()); }
        if (request.getCourseId() != null) { question.setCourseId(request.getCourseId()); }
        if (request.getDifficulty() != null) { question.setDifficulty(request.getDifficulty()); }
        if (request.getAnalysis() != null) { question.setAnalysis(request.getAnalysis()); }
        if (request.getTags() != null) { question.setTags(request.getTags()); }
        if (request.getScore() != null) { question.setScore(request.getScore()); }
        questionMapper.updateById(question);

        // 更新选项：先删除旧选项，再插入新选项
        if (request.getOptions() != null) {
            LambdaQueryWrapper<QuestionOption> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(QuestionOption::getQuestionId, id);
            questionOptionMapper.delete(deleteWrapper);

            for (QuestionCreateRequest.OptionItem item : request.getOptions()) {
                QuestionOption option = new QuestionOption();
                option.setQuestionId(id);
                option.setContent(item.getContent());
                option.setOptionLabel(item.getOptionLabel());
                option.setIsCorrect(item.getIsCorrect() != null ? item.getIsCorrect() : 0);
                option.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                option.setDeleted(0);
                questionOptionMapper.insert(option);
            }
        }

        // 更新知识点关联：先删除旧关联，再插入新关联
        if (request.getKnowledgePointIds() != null) {
            LambdaQueryWrapper<QuestionKnowledgePoint> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(QuestionKnowledgePoint::getQuestionId, id);
            questionKnowledgePointMapper.delete(deleteWrapper);

            for (Long kpId : request.getKnowledgePointIds()) {
                KnowledgePoint kp = knowledgePointMapper.selectById(kpId);
                if (kp == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "知识点不存在: " + kpId);
                }
                QuestionKnowledgePoint qkp = new QuestionKnowledgePoint();
                qkp.setQuestionId(id);
                qkp.setKnowledgePointId(kpId);
                questionKnowledgePointMapper.insert(qkp);
            }
        }

        questionVersionService.recordChangeSnapshots(id, "UPDATE", operatorId,
                "更新题目内容、选项或知识点", snapshotBefore,
                questionVersionService.buildSnapshotJson(questionMapper.selectById(id)));
        return getQuestionById(id);
    }

    /**
     * 删除题目（级联删除选项和知识点关联）
     */
    @CacheEvict(value = "questionReviewSuggestion", key = "#id")
    @Transactional
    public void deleteQuestion(Long id, Long operatorId) {
        Question question = questionMapper.selectById(id);
        if (question == null || "PRIVATE".equals(question.getVisibility())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
        ensureNotUsedByPublishedPaper(id);
        String snapshotBefore = questionVersionService.buildSnapshotJson(question);
        // 删除题目（逻辑删除）
        questionMapper.deleteById(id);
        // 删除选项（逻辑删除）
        LambdaQueryWrapper<QuestionOption> optionWrapper = new LambdaQueryWrapper<>();
        optionWrapper.eq(QuestionOption::getQuestionId, id);
        questionOptionMapper.delete(optionWrapper);
        // 删除知识点关联（物理删除）
        LambdaQueryWrapper<QuestionKnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
        kpWrapper.eq(QuestionKnowledgePoint::getQuestionId, id);
        questionKnowledgePointMapper.delete(kpWrapper);
        questionVersionService.recordChangeSnapshots(id, "DELETE", operatorId,
                "删除题目", snapshotBefore, null);
    }

    /**
     * 填充 QuestionVO 的选项和知识点信息
     */
    private void fillQuestionVO(QuestionVO vo) {
        // 填充课程名称
        Course course = courseMapper.selectById(vo.getCourseId());
        if (course != null) {
            vo.setCourseName(course.getName());
        }

        // 填充选项
        LambdaQueryWrapper<QuestionOption> optionWrapper = new LambdaQueryWrapper<>();
        optionWrapper.eq(QuestionOption::getQuestionId, vo.getId())
                     .orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOption> options = questionOptionMapper.selectList(optionWrapper);
        vo.setOptions(options.stream()
                .map(QuestionOptionVO::fromEntity)
                .collect(Collectors.toList()));

        // 填充知识点关联
        LambdaQueryWrapper<QuestionKnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
        kpWrapper.eq(QuestionKnowledgePoint::getQuestionId, vo.getId());
        List<QuestionKnowledgePoint> qkps = questionKnowledgePointMapper.selectList(kpWrapper);
        List<Long> kpIds = qkps.stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId)
                .collect(Collectors.toList());
        vo.setKnowledgePointIds(kpIds);

        // 填充知识点名称
        List<String> kpNames = new ArrayList<>();
        for (Long kpId : kpIds) {
            KnowledgePoint kp = knowledgePointMapper.selectById(kpId);
            if (kp != null) {
                kpNames.add(kp.getName());
            }
        }
        vo.setKnowledgePointNames(kpNames);
    }

    private void fillQuestionVOForUser(QuestionVO vo) {
        fillQuestionVO(vo);
        if (vo.getOptions() != null) {
            vo.getOptions().forEach(option -> option.setIsCorrect(null));
        }
    }

    private QuestionDuplicateGroupVO buildDuplicateGroup(QuestionDuplicateDetector.DuplicateGroup group) {
        QuestionDuplicateGroupVO vo = new QuestionDuplicateGroupVO();
        vo.setMatchType(group.matchType());
        vo.setSimilarityScore(group.similarityScore());
        vo.setRepresentativeContent(group.questions().get(0).getContent());
        vo.setQuestions(group.questions().stream()
                .map(question -> {
                    QuestionVO questionVO = QuestionVO.fromEntity(question);
                    fillQuestionVO(questionVO);
                    return questionVO;
                })
                .collect(Collectors.toList()));
        return vo;
    }

    private void ensureNotUsedByPublishedPaper(Long questionId) {
        if (examQuestionMapper.countPublishedPapersByQuestionId(questionId) > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "题目已用于已发布试卷，不能修改或删除");
        }
    }
}
