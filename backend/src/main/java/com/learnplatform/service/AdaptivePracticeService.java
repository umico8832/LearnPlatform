package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.dto.QuestionOptionVO;
import com.learnplatform.service.question.AdaptivePracticePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Map<Integer, AdaptivePracticePolicy.DifficultyStats> statsMap = calculateDifficultyStats(userId);

        // 2. 计算各难度级别的权重
        double[] weights = AdaptivePracticePolicy.calculateWeights(statsMap);

        log.info("自适应推荐: userId={}, weights=[{},{},{},{},{}]",
                userId,
                AdaptivePracticePolicy.round(weights[0]), AdaptivePracticePolicy.round(weights[1]),
                AdaptivePracticePolicy.round(weights[2]), AdaptivePracticePolicy.round(weights[3]),
                AdaptivePracticePolicy.round(weights[4]));

        // 3. 排除近期做过的题目（最近 20 道）
        Set<Long> recentQuestionIds = getRecentQuestionIds(userId, 20);

        // 4. 按权重分配各难度应抽取的题目数量
        int[] countsPerDifficulty = AdaptivePracticePolicy.allocateCounts(weights, count);

        // 5. 从各难度级别选题
        List<Question> selected = new ArrayList<>();
        for (int diff = 1; diff <= 5; diff++) {
            int needed = countsPerDifficulty[diff - 1];
            if (needed <= 0) { continue; }

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
        Map<Integer, AdaptivePracticePolicy.DifficultyStats> statsMap = calculateDifficultyStats(userId);
        double[] weights = AdaptivePracticePolicy.calculateWeights(statsMap);
        return AdaptivePracticePolicy.buildSummary(statsMap, weights);
    }

    // ======================== 私有方法 ========================

    /**
     * 计算用户各难度级别的答题统计
     */
    private Map<Integer, AdaptivePracticePolicy.DifficultyStats> calculateDifficultyStats(Long userId) {
        LambdaQueryWrapper<PracticeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeRecord::getUserId, userId);
        List<PracticeRecord> records = practiceRecordMapper.selectList(wrapper);

        Map<Integer, int[]> counts = new HashMap<>();
        for (int diff = 1; diff <= 5; diff++) {
            counts.put(diff, new int[2]);
        }

        for (PracticeRecord record : records) {
            Question question = questionMapper.selectById(record.getQuestionId());
            if (question == null || question.getDifficulty() == null) { continue; }

            int diff = question.getDifficulty();
            if (diff < 1 || diff > 5) { continue; }

            int[] stats = counts.get(diff);
            stats[0]++;
            if (record.getIsCorrect() != null && record.getIsCorrect() == 1) {
                stats[1]++;
            }
        }
        Map<Integer, AdaptivePracticePolicy.DifficultyStats> statsMap = new HashMap<>();
        counts.forEach((difficulty, values) -> statsMap.put(
                difficulty, new AdaptivePracticePolicy.DifficultyStats(values[0], values[1])));
        return statsMap;
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
               .eq(Question::getVisibility, "PUBLIC")
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

}
