package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.LearningPathVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习路径推荐服务
 *
 * 基于用户在各知识点上的练习记录、错题分布和知识点层级关系，
 * 计算每个知识点的掌握程度并生成优先级排序的学习路径。
 */
@Service
public class LearningPathService {

    private static final Logger log = LoggerFactory.getLogger(LearningPathService.class);

    /** 掌握阈值（正确率 >= 70% 视为已掌握） */
    private static final double MASTERED_THRESHOLD = 70.0;
    /** 需要复习阈值（正确率 >= 50% 且 < 70%） */
    private static final double REVIEW_THRESHOLD = 50.0;
    /** 未练习过的知识点默认优先级 */
    private static final double NOT_STARTED_PRIORITY = 60.0;

    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final QuestionMapper questionMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final CourseMapper courseMapper;

    public LearningPathService(KnowledgePointMapper knowledgePointMapper,
                               QuestionKnowledgePointMapper questionKnowledgePointMapper,
                               QuestionMapper questionMapper,
                               PracticeRecordMapper practiceRecordMapper,
                               WrongQuestionMapper wrongQuestionMapper,
                               CourseMapper courseMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.questionMapper = questionMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 获取学习路径推荐
     *
     * @param userId   用户 ID
     * @param courseId 可选课程 ID，为 null 时分析所有课程
     * @return 学习路径推荐结果
     */
    @Cacheable(value = "learningPath", key = "#userId + ':' + #courseId")
    public LearningPathVO getLearningPath(Long userId, Long courseId) {
        log.info("生成学习路径推荐: userId={}, courseId={}", userId, courseId);

        // 1. 获取目标知识点列表
        LambdaQueryWrapper<KnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            kpWrapper.eq(KnowledgePoint::getCourseId, courseId);
        }
        List<KnowledgePoint> allPoints = knowledgePointMapper.selectList(kpWrapper);
        if (allPoints.isEmpty()) {
            return buildEmptyResult(courseId);
        }

        // 2. 获取用户所有练习记录
        LambdaQueryWrapper<PracticeRecord> prWrapper = new LambdaQueryWrapper<>();
        prWrapper.eq(PracticeRecord::getUserId, userId);
        List<PracticeRecord> allRecords = practiceRecordMapper.selectList(prWrapper);

        // 3. 构建题目 -> 知识点映射
        Map<Long, Set<Long>> questionToKps = buildQuestionToKps(allPoints);

        // 4. 构建知识点 -> 练习记录映射
        Map<Long, List<PracticeRecord>> kpRecords = new HashMap<>();
        for (PracticeRecord r : allRecords) {
            if (r.getQuestionId() == null) continue;
            Set<Long> kps = questionToKps.get(r.getQuestionId());
            if (kps != null) {
                for (Long kpId : kps) {
                    kpRecords.computeIfAbsent(kpId, k -> new ArrayList<>()).add(r);
                }
            }
        }

        // 5. 获取用户错题集
        LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
        wqWrapper.eq(WrongQuestion::getUserId, userId).eq(WrongQuestion::getDeleted, 0);
        List<WrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(wqWrapper);
        Map<Long, Long> wrongCountByQuestion = wrongQuestions.stream()
                .filter(w -> w.getQuestionId() != null)
                .collect(Collectors.groupingBy(WrongQuestion::getQuestionId, Collectors.counting()));
        // 按知识点统计错题数
        Map<Long, Integer> kpWrongCount = new HashMap<>();
        for (Map.Entry<Long, Long> entry : wrongCountByQuestion.entrySet()) {
            Set<Long> kps = questionToKps.get(entry.getKey());
            if (kps != null) {
                for (Long kpId : kps) {
                    kpWrongCount.merge(kpId, entry.getValue().intValue(), Integer::sum);
                }
            }
        }

        // 6. 计算每个知识点的掌握数据
        Map<Long, Course> courseCache = new HashMap<>();
        List<LearningPathVO.PathStep> steps = new ArrayList<>();
        int masteredCount = 0;
        int weakCount = 0;

        for (KnowledgePoint kp : allPoints) {
            List<PracticeRecord> records = kpRecords.getOrDefault(kp.getId(), Collections.emptyList());
            int total = records.size();
            long correct = records.stream()
                    .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
            double rate = total == 0 ? -1.0 : Math.round(correct * 1000.0 / total) / 10.0;
            int wrongs = kpWrongCount.getOrDefault(kp.getId(), 0);

            String masteryStatus;
            if (total == 0) {
                masteryStatus = "NOT_STARTED";
            } else if (rate >= MASTERED_THRESHOLD) {
                masteryStatus = "MASTERED";
                masteredCount++;
            } else if (rate >= REVIEW_THRESHOLD) {
                masteryStatus = "NEEDS_REVIEW";
                weakCount++;
            } else {
                masteryStatus = "WEAK";
                weakCount++;
            }

            // 计算优先级得分
            double priority = calculatePriority(rate, total, wrongs, masteryStatus);

            // 获取课程名称（缓存）
            Course course = courseCache.computeIfAbsent(kp.getCourseId(),
                    id -> courseMapper.selectById(id));

            LearningPathVO.PathStep step = new LearningPathVO.PathStep();
            step.setKnowledgePointId(kp.getId());
            step.setKnowledgePointName(kp.getName());
            step.setCourseId(kp.getCourseId());
            step.setCourseName(course != null ? course.getName() : "未知课程");
            step.setParentId(kp.getParentId());
            step.setCorrectRate(rate);
            step.setTotalAttempts(total);
            step.setWrongCount(wrongs);
            step.setMasteryStatus(masteryStatus);
            step.setPriorityScore(priority);
            step.setRecommendation(getRecommendation(masteryStatus, rate, total, wrongs));

            steps.add(step);
        }

        // 7. 按优先级排序
        steps.sort((a, b) -> Double.compare(b.getPriorityScore(), a.getPriorityScore()));
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setOrder(i + 1);
        }

        // 8. 计算各课程概况
        List<LearningPathVO.CourseOverview> courseOverviews = buildCourseOverviews(steps, allPoints, courseCache);

        // 9. 计算总体掌握率
        double overallMastery = allPoints.isEmpty() ? 0 :
                Math.round(masteredCount * 1000.0 / allPoints.size()) / 10.0;

        // 10. 组装结果
        LearningPathVO vo = new LearningPathVO();
        if (courseId != null) {
            Course c = courseCache.get(courseId);
            vo.setCourseName(c != null ? c.getName() : "未知课程");
        } else {
            vo.setCourseName("全部课程");
        }
        vo.setOverallMastery(overallMastery);
        vo.setTotalKnowledgePoints(allPoints.size());
        vo.setMasteredCount(masteredCount);
        vo.setWeakCount(weakCount);
        vo.setSteps(steps);
        vo.setCourseOverviews(courseOverviews);

        return vo;
    }

    // ======================== 私有方法 ========================

    /**
     * 构建题目 -> 知识点映射
     */
    private Map<Long, Set<Long>> buildQuestionToKps(List<KnowledgePoint> allPoints) {
        Set<Long> kpIds = allPoints.stream()
                .map(KnowledgePoint::getId)
                .collect(Collectors.toSet());

        // 查询这些知识点关联的所有题目
        LambdaQueryWrapper<QuestionKnowledgePoint> qkpWrapper = new LambdaQueryWrapper<>();
        qkpWrapper.in(QuestionKnowledgePoint::getKnowledgePointId, kpIds);
        List<QuestionKnowledgePoint> qkps = questionKnowledgePointMapper.selectList(qkpWrapper);

        Map<Long, Set<Long>> questionToKps = new HashMap<>();
        for (QuestionKnowledgePoint qkp : qkps) {
            questionToKps.computeIfAbsent(qkp.getQuestionId(), k -> new HashSet<>())
                    .add(qkp.getKnowledgePointId());
        }
        return questionToKps;
    }

    /**
     * 计算知识点优先级得分
     *
     * 得分越高，越需要优先学习。算法考虑：
     * - 正确率越低，优先级越高
     * - 错题越多，优先级越高
     * - 未练习过的知识点给一个中等优先级
     * - 已掌握的知识点优先级最低
     */
    private double calculatePriority(double rate, int total, int wrongs, String status) {
        switch (status) {
            case "NOT_STARTED":
                return NOT_STARTED_PRIORITY;
            case "MASTERED":
                // 已掌握的优先级最低，但错题多的仍有一点权重
                return Math.max(0, 5 + wrongs * 0.5);
            case "NEEDS_REVIEW":
                // 正确率 50-70%，中等优先级
                return 40 + (REVIEW_THRESHOLD - rate) * 0.5 + wrongs * 1.0;
            case "WEAK":
                // 正确率 < 50%，高优先级
                return 70 + (REVIEW_THRESHOLD - Math.max(rate, 0)) * 0.3 + wrongs * 1.5;
            default:
                return 0;
        }
    }

    /**
     * 根据掌握状态生成推荐建议
     */
    private String getRecommendation(String status, double rate, int total, int wrongs) {
        switch (status) {
            case "NOT_STARTED":
                return "该知识点尚未开始练习，建议先学习基础概念后再刷题巩固。";
            case "WEAK":
                return String.format("正确率 %.1f%%，共 %d 题 %d 道错题。建议重新学习核心概念，"
                        + "从基础题开始逐步提高难度。", rate, total, wrongs);
            case "NEEDS_REVIEW":
                return String.format("正确率 %.1f%%，有一定基础但不够扎实。建议做几道变式题巩固，"
                        + "重点关注错题涉及的知识盲区。", rate);
            case "MASTERED":
                return String.format("正确率 %.1f%%，掌握良好！可以偶尔复习保持记忆，"
                        + "或尝试更高难度的题目。", rate);
            default:
                return "";
        }
    }

    /**
     * 按课程构建掌握概况
     */
    private List<LearningPathVO.CourseOverview> buildCourseOverviews(
            List<LearningPathVO.PathStep> steps,
            List<KnowledgePoint> allPoints,
            Map<Long, Course> courseCache) {

        // 按课程分组统计知识点数
        Map<Long, Long> kpCountByCourse = allPoints.stream()
                .filter(kp -> kp.getCourseId() != null)
                .collect(Collectors.groupingBy(KnowledgePoint::getCourseId, Collectors.counting()));

        // 按课程分组统计步骤
        Map<Long, List<LearningPathVO.PathStep>> stepsByCourse = steps.stream()
                .filter(s -> s.getCourseId() != null)
                .collect(Collectors.groupingBy(LearningPathVO.PathStep::getCourseId));

        List<LearningPathVO.CourseOverview> overviews = new ArrayList<>();
        for (Map.Entry<Long, List<LearningPathVO.PathStep>> entry : stepsByCourse.entrySet()) {
            Long cId = entry.getKey();
            List<LearningPathVO.PathStep> cSteps = entry.getValue();
            Course course = courseCache.get(cId);

            int totalAttempts = cSteps.stream().mapToInt(LearningPathVO.PathStep::getTotalAttempts).sum();
            long totalCorrect = cSteps.stream()
                    .filter(s -> s.getCorrectRate() >= 0)
                    .mapToLong(s -> Math.round(s.getCorrectRate() * s.getTotalAttempts() / 100.0))
                    .sum();
            double courseRate = totalAttempts == 0 ? 0
                    : Math.round(totalCorrect * 1000.0 / totalAttempts) / 10.0;
            long masteredPoints = cSteps.stream()
                    .filter(s -> "MASTERED".equals(s.getMasteryStatus()))
                    .count();

            LearningPathVO.CourseOverview overview = new LearningPathVO.CourseOverview();
            overview.setCourseId(cId);
            overview.setCourseName(course != null ? course.getName() : "未知课程");
            overview.setCorrectRate(courseRate);
            overview.setTotalAttempts(totalAttempts);
            overview.setKnowledgePointCount(kpCountByCourse.getOrDefault(cId, 0L).intValue());
            overview.setMasteredPointCount((int) masteredPoints);
            overviews.add(overview);
        }

        overviews.sort((a, b) -> Double.compare(a.getCorrectRate(), b.getCorrectRate()));
        return overviews;
    }

    /**
     * 构建空结果
     */
    private LearningPathVO buildEmptyResult(Long courseId) {
        LearningPathVO vo = new LearningPathVO();
        if (courseId != null) {
            Course c = courseMapper.selectById(courseId);
            vo.setCourseName(c != null ? c.getName() : "未知课程");
        } else {
            vo.setCourseName("全部课程");
        }
        vo.setOverallMastery(0);
        vo.setTotalKnowledgePoints(0);
        vo.setMasteredCount(0);
        vo.setWeakCount(0);
        vo.setSteps(Collections.emptyList());
        vo.setCourseOverviews(Collections.emptyList());
        return vo;
    }
}