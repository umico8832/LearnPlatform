package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamPaperCreateRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 智能组卷服务
 * 根据课程知识点覆盖、难度分布和用户薄弱环节自动选择题目生成试卷
 */
@Service
public class AiExamGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AiExamGenerationService.class);

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final ExamPaperService examPaperService;

    public AiExamGenerationService(QuestionMapper questionMapper,
                                    QuestionOptionMapper questionOptionMapper,
                                    QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                    KnowledgePointMapper knowledgePointMapper,
                                    PracticeRecordMapper practiceRecordMapper,
                                    WrongQuestionMapper wrongQuestionMapper,
                                    ExamPaperService examPaperService) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.examPaperService = examPaperService;
    }

    /**
     * 智能组卷请求参数
     */
    public static class SmartExamRequest {
        private Long courseId;
        private Integer questionCount = 20;
        /** 难度分布偏好：EASY/BALANCED/HARD/ADAPTIVE，默认 ADAPTIVE */
        private String difficultyMode = "ADAPTIVE";
        /** 是否优先包含用户的错题 */
        private boolean includeWrongQuestions = true;
        /** 试卷标题（可选，为空则自动生成） */
        private String title;
        /** 考试时长（分钟） */
        private Integer duration = 60;

        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public Integer getQuestionCount() { return questionCount; }
        public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
        public String getDifficultyMode() { return difficultyMode; }
        public void setDifficultyMode(String difficultyMode) { this.difficultyMode = difficultyMode; }
        public boolean isIncludeWrongQuestions() { return includeWrongQuestions; }
        public void setIncludeWrongQuestions(boolean includeWrongQuestions) {
            this.includeWrongQuestions = includeWrongQuestions;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
    }

    /**
     * 智能组卷结果预览（不含实际创建试卷）
     */
    public static class SmartExamPreview {
        private String title;
        private String description;
        private Long courseId;
        private String courseName;
        private Integer questionCount;
        private Integer totalScore;
        private Integer duration;
        /** 各知识点题目数分布 */
        private Map<String, Integer> knowledgePointDistribution;
        /** 各难度题目数分布 */
        private Map<String, Integer> difficultyDistribution;
        /** 选中的题目 ID 列表 */
        private List<Long> questionIds;
        /** 推荐理由 */
        private String recommendation;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public Integer getQuestionCount() { return questionCount; }
        public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
        public Integer getTotalScore() { return totalScore; }
        public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
        public Map<String, Integer> getKnowledgePointDistribution() { return knowledgePointDistribution; }
        public void setKnowledgePointDistribution(Map<String, Integer> knowledgePointDistribution) {
            this.knowledgePointDistribution = knowledgePointDistribution;
        }
        public Map<String, Integer> getDifficultyDistribution() { return difficultyDistribution; }
        public void setDifficultyDistribution(Map<String, Integer> difficultyDistribution) {
            this.difficultyDistribution = difficultyDistribution;
        }
        public List<Long> getQuestionIds() { return questionIds; }
        public void setQuestionIds(List<Long> questionIds) { this.questionIds = questionIds; }
        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    }

    /**
     * 智能组卷预览：分析题库并推荐题目组合
     */
    public SmartExamPreview preview(SmartExamRequest request, Long userId) {
        int questionCount = request.getQuestionCount() != null ? request.getQuestionCount() : 20;
        if (questionCount <= 0 || questionCount > 100) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "题目数量应在 1-100 之间");
        }

        // 1. 查询题库中可用题目
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<>();
        qw.eq(Question::getStatus, 1); // 仅启用的题目
        qw.eq(Question::getVisibility, "PUBLIC");
        if (request.getCourseId() != null) {
            qw.eq(Question::getCourseId, request.getCourseId());
        }
        List<Question> availableQuestions = questionMapper.selectList(qw);
        if (availableQuestions.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "题库中没有可用的题目");
        }

        // 2. 获取知识点映射
        Map<Long, List<Long>> questionKpMap = buildQuestionKnowledgePointMap(availableQuestions);
        Map<Long, String> kpNameMap = buildKnowledgePointNameMap();

        // 3. 获取用户错题 ID 集合
        Set<Long> wrongQuestionIds = Collections.emptySet();
        if (userId != null && request.isIncludeWrongQuestions()) {
            wrongQuestionIds = getUserWrongQuestionIds(userId);
        }

        // 4. 获取用户历史正确率（用于自适应难度）
        Map<Integer, Double> difficultyAccuracy = Collections.emptyMap();
        if (userId != null && "ADAPTIVE".equals(request.getDifficultyMode())) {
            difficultyAccuracy = getUserDifficultyAccuracy(userId);
        }

        // 5. 计算每个题目的优先级分数
        Map<Long, Double> questionScores = new HashMap<>();
        for (Question q : availableQuestions) {
            double score = calculateQuestionPriority(q, questionKpMap, wrongQuestionIds, difficultyAccuracy, request);
            questionScores.put(q.getId(), score);
        }

        // 6. 按照难度模式计算各难度目标数量
        Map<Integer, Integer> difficultyTargets = calculateDifficultyTargets(questionCount,
                request.getDifficultyMode(), difficultyAccuracy);

        // 7. 知识点均衡选择
        List<Long> selectedIds = selectQuestionsBalanced(
                availableQuestions, questionScores, questionKpMap, difficultyTargets, questionCount);

        // 8. 构建预览结果
        SmartExamPreview preview = new SmartExamPreview();
        preview.setTitle(request.getTitle() != null ? request.getTitle() : generateTitle(request));
        preview.setDescription(generateDescription(request, selectedIds, questionKpMap, kpNameMap));
        preview.setCourseId(request.getCourseId());
        preview.setQuestionCount(selectedIds.size());
        preview.setTotalScore(selectedIds.size()); // 默认每题 1 分
        preview.setDuration(request.getDuration() != null ? request.getDuration() : 60);
        preview.setQuestionIds(selectedIds);

        // 知识点分布
        Map<String, Integer> kpDist = new LinkedHashMap<>();
        for (Long qId : selectedIds) {
            List<Long> kps = questionKpMap.getOrDefault(qId, Collections.emptyList());
            for (Long kpId : kps) {
                String name = kpNameMap.getOrDefault(kpId, "未知知识点");
                kpDist.merge(name, 1, Integer::sum);
            }
        }
        preview.setKnowledgePointDistribution(kpDist);

        // 难度分布
        Map<String, Integer> diffDist = new LinkedHashMap<>();
        Map<Integer, String> diffLabels = Map.of(1, "★", 2, "★★", 3, "★★★", 4, "★★★★", 5, "★★★★★");
        for (Long qId : selectedIds) {
            Question q = availableQuestions.stream().filter(aq -> aq.getId().equals(qId)).findFirst().orElse(null);
            if (q != null && q.getDifficulty() != null) {
                diffDist.merge(diffLabels.getOrDefault(q.getDifficulty(), "未知"), 1, Integer::sum);
            }
        }
        preview.setDifficultyDistribution(diffDist);

        // 课程名称
        if (request.getCourseId() != null) {
            Course course = new Course();
            course.setId(request.getCourseId());
            // 简单通过题目获取课程名
            availableQuestions.stream()
                    .filter(q -> q.getCourseId() != null && q.getCourseId().equals(request.getCourseId()))
                    .findFirst()
                    .ifPresent(q -> preview.setCourseName(null)); // 将在前端显示
        }

        // 推荐理由
        preview.setRecommendation(buildRecommendation(request, selectedIds, wrongQuestionIds, difficultyAccuracy));

        log.info("智能组卷预览: userId={}, courseId={}, questionCount={}, selectedCount={}",
                userId, request.getCourseId(), questionCount, selectedIds.size());

        return preview;
    }

    /**
     * 确认创建智能试卷
     */
    public ExamPaperVO createSmartExam(SmartExamPreview preview, Long adminUserId) {
        ExamPaperCreateRequest createRequest = new ExamPaperCreateRequest();
        createRequest.setTitle(preview.getTitle());
        createRequest.setDescription(preview.getDescription());
        createRequest.setCourseId(preview.getCourseId());
        createRequest.setDuration(preview.getDuration());
        createRequest.setStatus(0); // 默认草稿状态

        List<ExamPaperCreateRequest.QuestionItem> items = new ArrayList<>();
        int order = 1;
        for (Long qId : preview.getQuestionIds()) {
            ExamPaperCreateRequest.QuestionItem item = new ExamPaperCreateRequest.QuestionItem();
            item.setQuestionId(qId);
            item.setSortOrder(order++);
            item.setScore(1);
            items.add(item);
        }
        createRequest.setQuestions(items);

        ExamPaperVO vo = examPaperService.createExamPaper(createRequest, adminUserId);
        log.info("智能组卷创建成功: paperId={}, title={}, questionCount={}", vo.getId(), vo.getTitle(), vo.getQuestionCount());
        return vo;
    }

    // ======================== 私有方法 ========================

    /**
     * 计算题目优先级分数
     */
    private double calculateQuestionPriority(Question question,
                                              Map<Long, List<Long>> questionKpMap,
                                              Set<Long> wrongQuestionIds,
                                              Map<Integer, Double> difficultyAccuracy,
                                              SmartExamRequest request) {
        double score = 0.0;

        // 错题加权（+30）
        if (wrongQuestionIds.contains(question.getId())) {
            score += 30.0;
        }

        // 难度适配加权
        if (question.getDifficulty() != null && !difficultyAccuracy.isEmpty()) {
            score += calculateDifficultyWeight(question.getDifficulty(), difficultyAccuracy,
                    request.getDifficultyMode());
        }

        // 有解析的题目优先（+5）
        if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
            score += 5.0;
        }

        // 多题型覆盖加分
        String type = question.getQuestionType();
        if (type != null) {
            score += 2.0; // 基础分
        }

        // 添加随机扰动避免每次都生成相同试卷
        score += Math.random() * 10.0;

        return score;
    }

    /**
     * 根据难度模式和用户正确率计算难度权重
     */
    private double calculateDifficultyWeight(int difficulty, Map<Integer, Double> accuracy, String mode) {
        switch (mode) {
            case "EASY":
                return difficulty <= 2 ? 15.0 : (difficulty == 3 ? 5.0 : -5.0);
            case "HARD":
                return difficulty >= 4 ? 15.0 : (difficulty == 3 ? 5.0 : -5.0);
            case "ADAPTIVE":
                Double userAcc = accuracy.get(difficulty);
                if (userAcc == null) { return 5.0; } // 没做过该难度，给基础分
                // 正确率高的难度推荐更高难度，正确率低的推荐巩固
                if (userAcc > 0.8 && difficulty < 5) { return 15.0; } // 太简单了，推荐更难
                if (userAcc < 0.5) { return 10.0; } // 这个难度不够熟练，推荐练习
                return 8.0; // 适中
            default: // BALANCED
                return 8.0;
        }
    }

    /**
     * 计算各难度目标数量
     */
    private Map<Integer, Integer> calculateDifficultyTargets(int total, String mode, Map<Integer, Double> accuracy) {
        Map<Integer, Integer> targets = new LinkedHashMap<>();
        switch (mode) {
            case "EASY":
                targets.put(1, (int) Math.ceil(total * 0.3));
                targets.put(2, (int) Math.ceil(total * 0.35));
                targets.put(3, (int) Math.ceil(total * 0.25));
                targets.put(4, (int) Math.floor(total * 0.1));
                targets.put(5, 0);
                break;
            case "HARD":
                targets.put(1, 0);
                targets.put(2, (int) Math.floor(total * 0.1));
                targets.put(3, (int) Math.ceil(total * 0.25));
                targets.put(4, (int) Math.ceil(total * 0.35));
                targets.put(5, (int) Math.ceil(total * 0.3));
                break;
            case "ADAPTIVE":
                // 根据用户各难度正确率自适应分配
                for (int d = 1; d <= 5; d++) {
                    Double acc = accuracy.get(d);
                    if (acc == null) {
                        targets.put(d, (int) Math.ceil(total * 0.2));
                    } else if (acc > 0.8) {
                        targets.put(d, (int) Math.ceil(total * 0.3)); // 提升难度占比
                    } else if (acc < 0.4) {
                        targets.put(d, (int) Math.ceil(total * 0.3)); // 巩固薄弱难度
                    } else {
                        targets.put(d, (int) Math.ceil(total * 0.2));
                    }
                }
                break;
            default: // BALANCED
                int perLevel = total / 5;
                int remainder = total % 5;
                for (int d = 1; d <= 5; d++) {
                    targets.put(d, perLevel + (d <= remainder ? 1 : 0));
                }
        }
        return targets;
    }

    /**
     * 知识点均衡选择算法
     * 确保各知识点都有覆盖，同时满足难度分布要求
     */
    private List<Long> selectQuestionsBalanced(List<Question> questions,
                                                Map<Long, Double> scores,
                                                Map<Long, List<Long>> questionKpMap,
                                                Map<Integer, Integer> difficultyTargets,
                                                int totalCount) {
        // 按分数降序排列
        List<Question> sorted = questions.stream()
                .sorted((a, b) -> Double.compare(scores.getOrDefault(b.getId(), 0.0),
                        scores.getOrDefault(a.getId(), 0.0)))
                .collect(Collectors.toList());

        Set<Long> selected = new LinkedHashSet<>();
        Map<Integer, Integer> difficultyCount = new HashMap<>();
        Set<Long> coveredKps = new HashSet<>();

        // 第一轮：确保每个知识点至少有一题
        Set<Long> allKps = questionKpMap.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        for (Long kpId : allKps) {
            if (selected.size() >= totalCount) { break; }
            Question bestForKp = sorted.stream()
                    .filter(q -> !selected.contains(q.getId()))
                    .filter(q -> {
                        List<Long> kps = questionKpMap.get(q.getId());
                        return kps != null && kps.contains(kpId);
                    })
                    .findFirst()
                    .orElse(null);
            if (bestForKp != null) {
                selected.add(bestForKp.getId());
                coveredKps.add(kpId);
                difficultyCount.merge(bestForKp.getDifficulty() != null ? bestForKp.getDifficulty() : 3,
                        1, Integer::sum);
            }
        }

        // 第二轮：按难度目标补充
        for (Map.Entry<Integer, Integer> entry : difficultyTargets.entrySet()) {
            int diff = entry.getKey();
            int target = entry.getValue();
            int current = difficultyCount.getOrDefault(diff, 0);
            while (current < target && selected.size() < totalCount) {
                Question candidate = sorted.stream()
                        .filter(q -> !selected.contains(q.getId()))
                        .filter(q -> Objects.equals(q.getDifficulty(), diff))
                        .findFirst()
                        .orElse(null);
                if (candidate == null) { break; }
                selected.add(candidate.getId());
                current++;
                difficultyCount.put(diff, current);
            }
        }

        // 第三轮：补充剩余题目
        for (Question q : sorted) {
            if (selected.size() >= totalCount) { break; }
            selected.add(q.getId());
        }

        return new ArrayList<>(selected);
    }

    /**
     * 构建题目-知识点映射
     */
    private Map<Long, List<Long>> buildQuestionKnowledgePointMap(List<Question> questions) {
        if (questions.isEmpty()) { return Collections.emptyMap(); }

        List<Long> qIds = questions.stream().map(Question::getId).collect(Collectors.toList());
        LambdaQueryWrapper<QuestionKnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(QuestionKnowledgePoint::getQuestionId, qIds);
        List<QuestionKnowledgePoint> qkps = questionKnowledgePointMapper.selectList(wrapper);

        Map<Long, List<Long>> map = new HashMap<>();
        for (QuestionKnowledgePoint qkp : qkps) {
            map.computeIfAbsent(qkp.getQuestionId(), k -> new ArrayList<>()).add(qkp.getKnowledgePointId());
        }
        return map;
    }

    /**
     * 构建知识点 ID -> 名称映射
     */
    private Map<Long, String> buildKnowledgePointNameMap() {
        List<KnowledgePoint> allKps = knowledgePointMapper.selectList(null);
        return allKps.stream().collect(Collectors.toMap(KnowledgePoint::getId, KnowledgePoint::getName));
    }

    /**
     * 获取用户错题 ID 集合
     */
    private Set<Long> getUserWrongQuestionIds(Long userId) {
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getDeleted, 0);
        return wrongQuestionMapper.selectList(wrapper).stream()
                .map(WrongQuestion::getQuestionId)
                .collect(Collectors.toSet());
    }

    /**
     * 获取用户各难度历史正确率
     */
    private Map<Integer, Double> getUserDifficultyAccuracy(Long userId) {
        LambdaQueryWrapper<PracticeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeRecord::getUserId, userId);
        List<PracticeRecord> records = practiceRecordMapper.selectList(wrapper);

        Map<Integer, int[]> stats = new HashMap<>(); // difficulty -> [correct, total]
        for (PracticeRecord record : records) {
            if (record.getQuestionId() == null) { continue; }
            Question q = questionMapper.selectById(record.getQuestionId());
            if (q == null || q.getDifficulty() == null) { continue; }
            int[] s = stats.computeIfAbsent(q.getDifficulty(), k -> new int[]{0, 0});
            s[1]++;
            if (record.getIsCorrect() != null && record.getIsCorrect() == 1) {
                s[0]++;
            }
        }

        Map<Integer, Double> accuracy = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : stats.entrySet()) {
            int[] s = entry.getValue();
            if (s[1] > 0) {
                accuracy.put(entry.getKey(), (double) s[0] / s[1]);
            }
        }
        return accuracy;
    }

    private String generateTitle(SmartExamRequest request) {
        StringBuilder sb = new StringBuilder("智能模拟试卷");
        sb.append("（").append(request.getDifficultyMode()).append("）");
        return sb.toString();
    }

    private String generateDescription(SmartExamRequest request,
                                        List<Long> selectedIds,
                                        Map<Long, List<Long>> questionKpMap,
                                        Map<Long, String> kpNameMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("由 AI 智能组卷系统自动生成。");
        sb.append("题目数量：").append(selectedIds.size()).append(" 道。");

        Set<Long> coveredKps = new HashSet<>();
        for (Long qId : selectedIds) {
            List<Long> kps = questionKpMap.getOrDefault(qId, Collections.emptyList());
            coveredKps.addAll(kps);
        }
        sb.append("覆盖知识点：").append(coveredKps.size()).append(" 个。");

        if (request.isIncludeWrongQuestions()) {
            sb.append("已包含用户易错题目。");
        }

        String modeDesc;
        switch (request.getDifficultyMode()) {
            case "EASY":
                modeDesc = "偏基础";
                break;
            case "HARD":
                modeDesc = "偏进阶";
                break;
            case "ADAPTIVE":
                modeDesc = "自适应";
                break;
            default:
                modeDesc = "均衡";
        }
        sb.append("难度模式：").append(modeDesc).append("。");

        return sb.toString();
    }

    private String buildRecommendation(SmartExamRequest request,
                                        List<Long> selectedIds,
                                        Set<Long> wrongQuestionIds,
                                        Map<Integer, Double> difficultyAccuracy) {
        StringBuilder sb = new StringBuilder();

        long wrongIncluded = selectedIds.stream().filter(wrongQuestionIds::contains).count();
        if (wrongIncluded > 0) {
            sb.append("本次试卷包含 ").append(wrongIncluded).append(" 道您的易错题，建议重点复习。");
        }

        if ("ADAPTIVE".equals(request.getDifficultyMode()) && !difficultyAccuracy.isEmpty()) {
            sb.append("基于您的历史答题表现，");
            difficultyAccuracy.entrySet().stream()
                    .filter(e -> e.getValue() < 0.5)
                    .forEach(e -> sb.append("★".repeat(e.getKey())).append("难度正确率较低，已适当增加练习。"));
        }

        if (sb.length() == 0) {
            sb.append("试卷已根据知识点覆盖和难度均衡原则智能生成。");
        }

        return sb.toString();
    }
}
