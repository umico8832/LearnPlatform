package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.dto.QuestionOptionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目难度自适应推荐服务
 * 根据用户各难度级别的历史答题表现动态推荐合适难度的题目
 */
@Service
public class AdaptivePracticeService {

    private static final Logger log = LoggerFactory.getLogger(AdaptivePracticeService.class);

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    public AdaptivePracticeService(QuestionMapper questionMapper,
                                   QuestionOptionMapper questionOptionMapper,
                                   QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                   PracticeRecordMapper practiceRecordMapper,
                                   CourseMapper courseMapper,
                                   KnowledgePointMapper knowledgePointMapper) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
    }

    /**
     * 自适应获取练习题目
     *
     * 算法：
     * 1. 查询用户各难度级别的答题正确率
     * 2. 根据整体表现确定目标难度区间
     * 3. 使用加权概率采样分配题目到各难度级别
     * 4. 排除近期做过的题目（最近 20 条记录中的题目）
     *
     * @param userId           用户ID
     * @param courseId         课程ID（可选）
     * @param knowledgePointId 知识点ID（可选）
     * @param questionType     题型（可选）
     * @param count            题目数量
     * @return 题目列表（不含答案）
     */
    public List<QuestionVO> getAdaptiveQuestions(Long userId, Long courseId,
                                                  Long knowledgePointId,
                                                  String questionType,
                                                  Integer count) {
        if (count == null || count <= 0) {
            count = 10;
        }
        if (count > 50) {
            count = 50;
        }

        // 1. 查询用户各难度级别答题统计
        Map<Integer, DifficultyStats> statsMap = calculateDifficultyStats(userId);

        // 2. 计算各难度级别的权重
        double[] weights = calculateWeights(statsMap);

        log.info("自适应推荐: userId={}, weights=[{},{},{},{},{}]",
                userId,
                round(weights[0]), round(weights[1]), round(weights[2]),
                round(weights[3]), round(weights[4]));

        // 3. 排除近期做过的题目（最近 20 道）
        Set<Long> recentQuestionIds = getRecentQuestionIds(userId, 20);

        // 4. 按权重分配各难度应抽取的题目数量
        int[] countsPerDifficulty = allocateCounts(weights, count);

        // 5. 从各难度级别选题
        List<Question> selected = new ArrayList<>();
        for (int diff = 1; diff <= 5; diff++) {
            int needed = countsPerDifficulty[diff - 1];
            if (needed <= 0) continue;

            List<Question> candidates = queryQuestions(diff, courseId,
                    knowledgePointId, questionType, recentQuestionIds);
            if (candidates.size() > needed) {
                Collections.shuffle(candidates);
                candidates = candidates.subList(0, needed);
            }
            selected.addAll(candidates);
        }

        // 6. 如果因排除近期题目导致不足，回退补充
        if (selected.size() < count) {
            int shortfall = count - selected.size();
            Set<Long> selectedIds = selected.stream()
                    .map(Question::getId).collect(Collectors.toSet());

            // 不限制排除列表，从所有候选题中补
            for (int diff = 5; diff >= 1 && shortfall > 0; diff--) {
                List<Question> extras = queryQuestions(diff, courseId,
                        knowledgePointId, questionType, Collections.emptySet());
                // 去除已选中的
                extras = extras.stream()
                        .filter(q -> !selectedIds.contains(q.getId()))
                        .collect(Collectors.toList());
                if (extras.size() > shortfall) {
                    Collections.shuffle(extras);
                    extras = extras.subList(0, shortfall);
                }
                selected.addAll(extras);
                selectedIds.addAll(extras.stream()
                        .map(Question::getId).collect(Collectors.toSet()));
                shortfall -= extras.size();
            }
        }

        // 最终打乱顺序
        Collections.shuffle(selected);

        log.info("自适应推荐结果: userId={}, requested={}, actual={}",
                userId, count, selected.size());

        // 转换为 VO
        return selected.stream().map(q -> {
            QuestionVO vo = QuestionVO.fromEntity(q);
            vo.setAnalysis(null);
            fillQuestionVOForPractice(vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 获取用户难度级别推荐摘要（供前端展示）
     */
    public Map<String, Object> getAdaptiveSummary(Long userId) {
        Map<Integer, DifficultyStats> statsMap = calculateDifficultyStats(userId);
        double[] weights = calculateWeights(statsMap);

        Map<String, Object> summary = new LinkedHashMap<>();

        // 整体信息
        int totalAnswered = statsMap.values().stream().mapToInt(s -> s.total).sum();
        double overallCorrectRate = totalAnswered > 0
                ? statsMap.values().stream().mapToInt(s -> s.correct).sum() * 100.0 / totalAnswered
                : 0;

        summary.put("totalAnswered", totalAnswered);
        summary.put("overallCorrectRate", round(overallCorrectRate));

        // 各难度详情
        List<Map<String, Object>> difficultyDetails = new ArrayList<>();
        String[] diffLabels = {"入门", "简单", "中等", "困难", "专家"};
        for (int diff = 1; diff <= 5; diff++) {
            DifficultyStats stats = statsMap.getOrDefault(diff, new DifficultyStats());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("difficulty", diff);
            detail.put("label", diffLabels[diff - 1]);
            detail.put("total", stats.total);
            detail.put("correct", stats.correct);
            detail.put("correctRate", stats.total > 0 ? round(stats.correct * 100.0 / stats.total) : 0);
            detail.put("weight", round(weights[diff - 1]));
            difficultyDetails.add(detail);
        }
        summary.put("difficultyDetails", difficultyDetails);

        // 推荐说明
        double avgTarget = 0;
        double weightSum = 0;
        for (int i = 0; i < 5; i++) {
            avgTarget += (i + 1) * weights[i];
            weightSum += weights[i];
        }
        double targetDiff = weightSum > 0 ? avgTarget / weightSum : 3.0;
        summary.put("recommendedDifficulty", round(targetDiff));

        return summary;
    }

    // ======================== 私有方法 ========================

    /**
     * 各难度级别的统计信息
     */
    private static class DifficultyStats {
        int total;
        int correct;
    }

    /**
     * 计算用户各难度级别的答题统计
     */
    private Map<Integer, DifficultyStats> calculateDifficultyStats(Long userId) {
        LambdaQueryWrapper<PracticeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeRecord::getUserId, userId);
        List<PracticeRecord> records = practiceRecordMapper.selectList(wrapper);

        Map<Integer, DifficultyStats> statsMap = new HashMap<>();
        for (int diff = 1; diff <= 5; diff++) {
            statsMap.put(diff, new DifficultyStats());
        }

        for (PracticeRecord record : records) {
            Question question = questionMapper.selectById(record.getQuestionId());
            if (question == null || question.getDifficulty() == null) continue;

            int diff = question.getDifficulty();
            if (diff < 1 || diff > 5) continue;

            DifficultyStats stats = statsMap.get(diff);
            stats.total++;
            if (record.getIsCorrect() != null && record.getIsCorrect() == 1) {
                stats.correct++;
            }
        }

        return statsMap;
    }

    /**
     * 根据答题表现计算各难度级别的选题权重
     * 
     * 核心策略：
     * - 每个难度有基础权重（1.0）
     * - 正确率 > 75%：该难度权重降低，更高难度权重增加（答得好→升级）
     * - 正确率 < 50%：该难度权重增加，更低难度权重也增加（答得差→降级巩固）
     * - 无答题记录的难度有适度权重（鼓励尝试新难度）
     */
    private double[] calculateWeights(Map<Integer, DifficultyStats> statsMap) {
        double[] weights = new double[5];

        // 基础权重
        for (int i = 0; i < 5; i++) {
            weights[i] = 1.0;
        }

        boolean hasAnyData = statsMap.values().stream().anyMatch(s -> s.total > 0);

        if (!hasAnyData) {
            // 新用户：偏好简单和中等难度
            weights[0] = 2.5; // diff 1: 入门
            weights[1] = 3.0; // diff 2: 简单
            weights[2] = 2.5; // diff 3: 中等
            weights[3] = 1.0; // diff 4: 困难
            weights[4] = 0.5; // diff 5: 专家
            return normalizeWeights(weights);
        }

        for (int diff = 1; diff <= 5; diff++) {
            DifficultyStats stats = statsMap.get(diff);
            if (stats.total == 0) {
                // 未做过的难度，给予适度鼓励权重
                weights[diff - 1] = 1.5;
                continue;
            }

            double correctRate = (double) stats.correct / stats.total;

            if (correctRate > 0.75) {
                // 表现优秀：降低当前难度，提升更高难度
                weights[diff - 1] = 0.5; // 降低当前
                if (diff < 5) {
                    weights[diff] += 2.0; // 提升更高一级难度
                }
                if (diff < 4) {
                    weights[diff + 1] += 0.5; // 也稍微提升高两级
                }
            } else if (correctRate >= 0.5) {
                // 表现适中：保持当前难度
                weights[diff - 1] += 2.0;
            } else {
                // 表现较差：加强当前和更低难度
                weights[diff - 1] += 2.5; // 当前难度巩固
                if (diff > 1) {
                    weights[diff - 2] += 1.5; // 降低一级巩固基础
                }
                if (diff > 2) {
                    weights[diff - 3] += 0.5; // 再降一级
                }
            }

            // 答题越多，权重数据越可信
            double confidenceFactor = Math.min(stats.total / 10.0, 1.0);
            weights[diff - 1] *= confidenceFactor + 0.3; // 最低保留 30% 权重
        }

        return normalizeWeights(weights);
    }

    /**
     * 归一化权重（使总和 = 1.0）
     */
    private double[] normalizeWeights(double[] weights) {
        double sum = 0;
        for (double w : weights) {
            sum += Math.max(w, 0.01); // 确保每个难度都有最小概率
        }
        double[] normalized = new double[5];
        for (int i = 0; i < 5; i++) {
            normalized[i] = Math.max(weights[i], 0.01) / sum;
        }
        return normalized;
    }

    /**
     * 按权重分配各难度应抽取的题目数量
     */
    private int[] allocateCounts(double[] weights, int total) {
        int[] counts = new int[5];
        double sum = 0;
        for (double w : weights) sum += w;

        int allocated = 0;
        for (int i = 0; i < 5; i++) {
            if (i == 4) {
                // 最后一个难度：分配剩余
                counts[i] = total - allocated;
            } else {
                counts[i] = (int) Math.round(weights[i] / sum * total);
                allocated += counts[i];
            }
        }

        // 确保每个至少 0
        for (int i = 0; i < 5; i++) {
            if (counts[i] < 0) counts[i] = 0;
        }

        return counts;
    }

    /**
     * 获取用户近期做过的题目ID（用于排除）
     */
    private Set<Long> getRecentQuestionIds(Long userId, int limit) {
        LambdaQueryWrapper<PracticeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeRecord::getUserId, userId)
               .orderByDesc(PracticeRecord::getCreateTime)
               .last("LIMIT " + limit);
        List<PracticeRecord> recent = practiceRecordMapper.selectList(wrapper);
        return recent.stream()
                .map(PracticeRecord::getQuestionId)
                .collect(Collectors.toSet());
    }

    /**
     * 查询指定难度的候选题目
     */
    private List<Question> queryQuestions(int difficulty, Long courseId,
                                           Long knowledgePointId,
                                           String questionType,
                                           Set<Long> excludeIds) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getStatus, 1)
               .eq(Question::getDifficulty, difficulty);

        if (courseId != null) {
            wrapper.eq(Question::getCourseId, courseId);
        }
        if (questionType != null && !questionType.isEmpty()) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        if (excludeIds != null && !excludeIds.isEmpty()) {
            wrapper.notIn(Question::getId, excludeIds);
        }

        // 如果指定了知识点ID
        if (knowledgePointId != null) {
            LambdaQueryWrapper<QuestionKnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
            kpWrapper.eq(QuestionKnowledgePoint::getKnowledgePointId, knowledgePointId);
            List<QuestionKnowledgePoint> qkps = questionKnowledgePointMapper.selectList(kpWrapper);
            List<Long> questionIds = qkps.stream()
                    .map(QuestionKnowledgePoint::getQuestionId)
                    .collect(Collectors.toList());
            if (questionIds.isEmpty()) {
                return new ArrayList<>();
            }
            wrapper.in(Question::getId, questionIds);
        }

        return questionMapper.selectList(wrapper);
    }

    /**
     * 填充 QuestionVO（练习模式，不返回正确答案标记）
     */
    private void fillQuestionVOForPractice(QuestionVO vo) {
        Course course = courseMapper.selectById(vo.getCourseId());
        if (course != null) {
            vo.setCourseName(course.getName());
        }

        LambdaQueryWrapper<QuestionOption> optionWrapper = new LambdaQueryWrapper<>();
        optionWrapper.eq(QuestionOption::getQuestionId, vo.getId())
                     .orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOption> options = questionOptionMapper.selectList(optionWrapper);
        vo.setOptions(options.stream().map(o -> {
            QuestionOptionVO optVo = QuestionOptionVO.fromEntity(o);
            optVo.setIsCorrect(0);
            return optVo;
        }).collect(Collectors.toList()));

        LambdaQueryWrapper<QuestionKnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
        kpWrapper.eq(QuestionKnowledgePoint::getQuestionId, vo.getId());
        List<QuestionKnowledgePoint> qkps = questionKnowledgePointMapper.selectList(kpWrapper);
        List<Long> kpIds = qkps.stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId)
                .collect(Collectors.toList());
        vo.setKnowledgePointIds(kpIds);

        List<String> kpNames = new ArrayList<>();
        for (Long kpId : kpIds) {
            KnowledgePoint kp = knowledgePointMapper.selectById(kpId);
            if (kp != null) {
                kpNames.add(kp.getName());
            }
        }
        vo.setKnowledgePointNames(kpNames);
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}