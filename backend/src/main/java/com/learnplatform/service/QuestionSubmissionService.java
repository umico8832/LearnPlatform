package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.*;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目投稿服务
 */
@Service
public class QuestionSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionSubmissionService.class);

    private final QuestionSubmissionMapper submissionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ObjectMapper objectMapper;

    public QuestionSubmissionService(QuestionSubmissionMapper submissionMapper,
                                     QuestionMapper questionMapper,
                                     QuestionOptionMapper questionOptionMapper,
                                     QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                     CourseMapper courseMapper,
                                     UserMapper userMapper,
                                     KnowledgePointMapper knowledgePointMapper,
                                     ObjectMapper objectMapper) {
        this.submissionMapper = submissionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.objectMapper = objectMapper;
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

        normalizeAndValidateRequest(request, qt);

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

        return convertToVO(submission);
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
        return convertPage(result);
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
        return convertPage(result);
    }

    /**
     * 查看投稿详情
     */
    public QuestionSubmissionVO getSubmissionById(Long id) {
        QuestionSubmission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿不存在");
        }
        return convertToVO(submission);
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

        return convertToVO(submission);
    }

    /**
     * 管理员将已通过的投稿入库为正式题目
     */
    @Transactional
    public QuestionSubmissionVO importSubmission(Long submissionId, Long adminId) {
        QuestionSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿不存在");
        }
        if (submission.getStatus() != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "只有已通过的投稿才能入库");
        }

        // 创建正式题目
        Question question = new Question();
        question.setContent(submission.getContent());
        question.setQuestionType(submission.getQuestionType());
        question.setCourseId(submission.getCourseId());
        question.setDifficulty(submission.getDifficulty());
        question.setAnalysis(submission.getAnalysis());
        question.setTags(submission.getTags());
        question.setScore(1);
        question.setStatus(1);
        question.setCreateBy(submission.getUserId());
        question.setDeleted(0);
        questionMapper.insert(question);

        insertQuestionOptions(question.getId(), submission);

        // 保存知识点关联
        if (submission.getKnowledgePointIds() != null && !submission.getKnowledgePointIds().isBlank()) {
            String[] idStrs = submission.getKnowledgePointIds().split(",");
            for (String idStr : idStrs) {
                try {
                    Long kpId = Long.parseLong(idStr.trim());
                    KnowledgePoint kp = knowledgePointMapper.selectById(kpId);
                    if (kp != null) {
                        QuestionKnowledgePoint qkp = new QuestionKnowledgePoint();
                        qkp.setQuestionId(question.getId());
                        qkp.setKnowledgePointId(kpId);
                        questionKnowledgePointMapper.insert(qkp);
                    }
                } catch (NumberFormatException e) {
                    log.warn("知识点ID格式错误: {}", idStr);
                }
            }
        }

        // 更新投稿状态
        submission.setStatus(3); // 已入库
        submission.setImportedQuestionId(question.getId());
        submissionMapper.updateById(submission);

        log.info("管理员 {} 将投稿 {} 入库为题目 {}", adminId, submissionId, question.getId());

        return convertToVO(submission);
    }

    /**
     * 投稿统计（管理端看板用）
     */
    public long countByStatus(int status) {
        LambdaQueryWrapper<QuestionSubmission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionSubmission::getStatus, status);
        return submissionMapper.selectCount(wrapper);
    }

    // ========== private ==========

    private boolean isValidQuestionType(String type) {
        return Arrays.asList("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE",
                "FILL_BLANK", "SHORT_ANSWER").contains(type);
    }

    private void normalizeAndValidateRequest(QuestionSubmissionRequest request, String questionType) {
        if ("SINGLE_CHOICE".equals(questionType) || "MULTIPLE_CHOICE".equals(questionType)) {
            List<OptionItem> options = parseOptionsJson(request.getOptionsJson());
            if (options.size() < 2) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "选择题至少需要 2 个选项");
            }
            int correctCount = 0;
            for (int i = 0; i < options.size(); i++) {
                OptionItem item = options.get(i);
                if (item.content == null || item.content.trim().isEmpty()) {
                    throw new BusinessException(ResultCode.VALIDATION_ERROR, "选项内容不能为空");
                }
                item.content = item.content.trim();
                item.label = normalizeOptionLabel(item, i);
                if (Boolean.TRUE.equals(item.isCorrect)) {
                    correctCount++;
                }
            }
            if ("SINGLE_CHOICE".equals(questionType) && correctCount != 1) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "单选题必须且只能有 1 个正确答案");
            }
            if ("MULTIPLE_CHOICE".equals(questionType) && correctCount < 1) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "多选题至少需要 1 个正确答案");
            }
            request.setOptionsJson(writeOptionsJson(options));
            return;
        }

        if ("TRUE_FALSE".equals(questionType)) {
            String normalizedAnswer = normalizeTrueFalseAnswer(request.getCorrectAnswer());
            request.setCorrectAnswer(normalizedAnswer);
            request.setOptionsJson(writeOptionsJson(List.of(
                    optionItem("正确", "A", "TRUE".equals(normalizedAnswer)),
                    optionItem("错误", "B", "FALSE".equals(normalizedAnswer))
            )));
            return;
        }

        if ("FILL_BLANK".equals(questionType) || "SHORT_ANSWER".equals(questionType)) {
            if (request.getCorrectAnswer() == null || request.getCorrectAnswer().trim().isEmpty()) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "填空题和简答题必须提供参考答案");
            }
            request.setCorrectAnswer(request.getCorrectAnswer().trim());
            request.setOptionsJson(null);
        }
    }

    private List<OptionItem> parseOptionsJson(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "选择题必须提供选项");
        }
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<List<OptionItem>>() {});
        } catch (Exception e) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "选项JSON格式不正确");
        }
    }

    private String writeOptionsJson(List<OptionItem> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "选项JSON序列化失败");
        }
    }

    private String normalizeOptionLabel(OptionItem item, int index) {
        String label = item.label != null ? item.label : item.optionLabel;
        if (label == null || label.trim().isEmpty()) {
            return String.valueOf((char) ('A' + index));
        }
        return label.trim().toUpperCase();
    }

    private String normalizeTrueFalseAnswer(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "判断题必须提供正确答案");
        }
        String normalized = answer.trim();
        if ("TRUE".equalsIgnoreCase(normalized) || "正确".equals(normalized)
                || "对".equals(normalized) || "A".equalsIgnoreCase(normalized)) {
            return "TRUE";
        }
        if ("FALSE".equalsIgnoreCase(normalized) || "错误".equals(normalized)
                || "错".equals(normalized) || "B".equalsIgnoreCase(normalized)) {
            return "FALSE";
        }
        throw new BusinessException(ResultCode.VALIDATION_ERROR, "判断题答案只能是正确/错误");
    }

    private OptionItem optionItem(String content, String label, boolean isCorrect) {
        OptionItem item = new OptionItem();
        item.content = content;
        item.label = label;
        item.optionLabel = label;
        item.isCorrect = isCorrect;
        return item;
    }

    private void insertQuestionOptions(Long questionId, QuestionSubmission submission) {
        String questionType = submission.getQuestionType();
        if ("FILL_BLANK".equals(questionType) || "SHORT_ANSWER".equals(questionType)) {
            insertOption(questionId, submission.getCorrectAnswer(), "ANSWER", true, 0);
            return;
        }

        if ("TRUE_FALSE".equals(questionType) || "SINGLE_CHOICE".equals(questionType)
                || "MULTIPLE_CHOICE".equals(questionType)) {
            List<OptionItem> options = parseOptionsJson(submission.getOptionsJson());
            for (int i = 0; i < options.size(); i++) {
                OptionItem item = options.get(i);
                insertOption(questionId, item.content, normalizeOptionLabel(item, i),
                        Boolean.TRUE.equals(item.isCorrect), i);
            }
        }
    }

    private void insertOption(Long questionId, String content, String label, boolean isCorrect, int sortOrder) {
        QuestionOption option = new QuestionOption();
        option.setQuestionId(questionId);
        option.setContent(content);
        option.setOptionLabel(label);
        option.setIsCorrect(isCorrect ? 1 : 0);
        option.setSortOrder(sortOrder);
        option.setDeleted(0);
        questionOptionMapper.insert(option);
    }

    private QuestionSubmissionVO convertToVO(QuestionSubmission s) {
        QuestionSubmissionVO vo = new QuestionSubmissionVO();
        vo.setId(s.getId());
        vo.setUserId(s.getUserId());
        vo.setContent(s.getContent());
        vo.setQuestionType(s.getQuestionType());
        vo.setCourseId(s.getCourseId());
        vo.setDifficulty(s.getDifficulty());
        vo.setAnalysis(s.getAnalysis());
        vo.setOptionsJson(s.getOptionsJson());
        vo.setCorrectAnswer(s.getCorrectAnswer());
        vo.setKnowledgePointIds(s.getKnowledgePointIds());
        vo.setTags(s.getTags());
        vo.setSource(s.getSource());
        vo.setStatus(s.getStatus());
        vo.setReviewComment(s.getReviewComment());
        vo.setReviewedBy(s.getReviewedBy());
        vo.setReviewedTime(s.getReviewedTime());
        vo.setImportedQuestionId(s.getImportedQuestionId());
        vo.setCreateTime(s.getCreateTime());
        vo.setUpdateTime(s.getUpdateTime());

        // 填充用户名
        if (s.getUserId() != null) {
            User user = userMapper.selectById(s.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setNickname(user.getNickname());
            }
        }
        // 填充审核人名
        if (s.getReviewedBy() != null) {
            User reviewer = userMapper.selectById(s.getReviewedBy());
            if (reviewer != null) {
                vo.setReviewedByName(reviewer.getNickname() != null ? reviewer.getNickname() : reviewer.getUsername());
            }
        }
        // 填充课程名
        if (s.getCourseId() != null) {
            Course course = courseMapper.selectById(s.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getName());
            }
        }
        return vo;
    }

    private Page<QuestionSubmissionVO> convertPage(Page<QuestionSubmission> page) {
        Page<QuestionSubmissionVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 选项 JSON 反序列化辅助类
     */
    public static class OptionItem {
        public String content;
        public String label;
        public String optionLabel;
        public Boolean isCorrect;
    }
}
