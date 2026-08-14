package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionDuplicateGroupVO;
import com.learnplatform.dto.QuestionOptionVO;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        List<Question> questions = questionMapper.selectList(wrapper);
        if (questions.size() < 2) {
            return List.of();
        }

        Map<Long, String> normalizedMap = new HashMap<>();
        List<Question> candidates = new ArrayList<>();
        for (Question question : questions) {
            String normalized = normalizeQuestionContent(question.getContent());
            if (normalized.length() >= 8) {
                normalizedMap.put(question.getId(), normalized);
                candidates.add(question);
            }
        }
        if (candidates.size() < 2) {
            return List.of();
        }

        DuplicateUnionFind unionFind = new DuplicateUnionFind(candidates.stream().map(Question::getId).toList());
        Map<String, Integer> pairScores = new HashMap<>();
        Map<String, List<Question>> buckets = candidates.stream()
                .collect(Collectors.groupingBy(q -> q.getCourseId() + "|" + q.getQuestionType()));

        for (List<Question> bucket : buckets.values()) {
            for (int i = 0; i < bucket.size(); i++) {
                Question left = bucket.get(i);
                String leftText = normalizedMap.get(left.getId());
                for (int j = i + 1; j < bucket.size(); j++) {
                    Question right = bucket.get(j);
                    String rightText = normalizedMap.get(right.getId());
                    int score = duplicateSimilarity(leftText, rightText);
                    if (score >= threshold) {
                        unionFind.union(left.getId(), right.getId());
                        pairScores.put(pairKey(left.getId(), right.getId()), score);
                    }
                }
            }
        }

        Map<Long, List<Question>> grouped = new LinkedHashMap<>();
        for (Question question : candidates) {
            Long root = unionFind.find(question.getId());
            grouped.computeIfAbsent(root, key -> new ArrayList<>()).add(question);
        }

        return grouped.values().stream()
                .filter(group -> group.size() > 1)
                .map(group -> buildDuplicateGroup(group, normalizedMap, pairScores))
                .sorted(Comparator
                        .comparing(QuestionDuplicateGroupVO::getSimilarityScore, Comparator.reverseOrder())
                        .thenComparing(group -> group.getQuestions().size(), Comparator.reverseOrder()))
                .limit(maxGroups)
                .collect(Collectors.toList());
    }

    /**
     * 创建题目（包含选项和知识点关联）
     */
    @Transactional
    public QuestionVO createQuestion(QuestionCreateRequest request, Long createBy) {
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
        question.setSourceType("MANUAL");
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
        if (request.getContent() != null) question.setContent(request.getContent());
        if (request.getQuestionType() != null) question.setQuestionType(request.getQuestionType());
        if (request.getCourseId() != null) question.setCourseId(request.getCourseId());
        if (request.getDifficulty() != null) question.setDifficulty(request.getDifficulty());
        if (request.getAnalysis() != null) question.setAnalysis(request.getAnalysis());
        if (request.getTags() != null) question.setTags(request.getTags());
        if (request.getScore() != null) question.setScore(request.getScore());
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

    private QuestionDuplicateGroupVO buildDuplicateGroup(List<Question> group, Map<Long, String> normalizedMap,
                                                         Map<String, Integer> pairScores) {
        List<Question> sortedQuestions = group.stream()
                .sorted(Comparator.comparing(Question::getId))
                .collect(Collectors.toList());
        Set<String> normalizedValues = sortedQuestions.stream()
                .map(q -> normalizedMap.get(q.getId()))
                .collect(Collectors.toCollection(HashSet::new));

        int bestScore = normalizedValues.size() == 1 ? 100 : 0;
        for (int i = 0; i < sortedQuestions.size(); i++) {
            for (int j = i + 1; j < sortedQuestions.size(); j++) {
                bestScore = Math.max(bestScore,
                        pairScores.getOrDefault(pairKey(sortedQuestions.get(i).getId(), sortedQuestions.get(j).getId()), 0));
            }
        }

        QuestionDuplicateGroupVO vo = new QuestionDuplicateGroupVO();
        vo.setMatchType(normalizedValues.size() == 1 ? "EXACT" : "SIMILAR");
        vo.setSimilarityScore(bestScore);
        vo.setRepresentativeContent(sortedQuestions.get(0).getContent());
        vo.setQuestions(sortedQuestions.stream()
                .map(question -> {
                    QuestionVO questionVO = QuestionVO.fromEntity(question);
                    fillQuestionVO(questionVO);
                    return questionVO;
                })
                .collect(Collectors.toList()));
        return vo;
    }

    private String normalizeQuestionContent(String content) {
        if (content == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        content.toLowerCase(Locale.ROOT).codePoints().forEach(codePoint -> {
            if (Character.isLetterOrDigit(codePoint)) {
                builder.appendCodePoint(codePoint);
            }
        });
        return builder.toString();
    }

    private int duplicateSimilarity(String left, String right) {
        if (left.equals(right)) {
            return 100;
        }
        int longer = Math.max(left.length(), right.length());
        int shorter = Math.min(left.length(), right.length());
        if (shorter < 8) {
            return 0;
        }
        if (left.contains(right) || right.contains(left)) {
            return Math.round(shorter * 100f / longer);
        }
        int distance = levenshteinDistance(left, right);
        return Math.max(0, Math.round((longer - distance) * 100f / longer));
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }

    private String pairKey(Long leftId, Long rightId) {
        return leftId < rightId ? leftId + ":" + rightId : rightId + ":" + leftId;
    }

    private static class DuplicateUnionFind {
        private final Map<Long, Long> parent = new HashMap<>();

        DuplicateUnionFind(List<Long> ids) {
            ids.forEach(id -> parent.put(id, id));
        }

        Long find(Long id) {
            Long currentParent = parent.get(id);
            if (currentParent == null || currentParent.equals(id)) {
                return id;
            }
            Long root = find(currentParent);
            parent.put(id, root);
            return root;
        }

        void union(Long left, Long right) {
            Long leftRoot = find(left);
            Long rightRoot = find(right);
            if (!leftRoot.equals(rightRoot)) {
                parent.put(rightRoot, leftRoot);
            }
        }
    }

    private void ensureNotUsedByPublishedPaper(Long questionId) {
        if (examQuestionMapper.countPublishedPapersByQuestionId(questionId) > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "题目已用于已发布试卷，不能修改或删除");
        }
    }
}
