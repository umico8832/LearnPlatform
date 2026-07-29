package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.dto.SimilarQuestionVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import com.learnplatform.service.ai.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习诊断服务
 *
 * 基于用户答题记录、错题本、知识点关联等数据，
 * 综合分析知识点薄弱诊断、错因模式、学习习惯并生成每日推荐。
 */
@Service
public class LearningDiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(LearningDiagnosisService.class);

    /** 薄弱知识点阈值 */
    private static final double WEAK_THRESHOLD = 50.0;
    /** 需要复习阈值 */
    private static final double REVIEW_THRESHOLD = 70.0;
    /** 推荐题目数量 */
    private static final int RECOMMEND_COUNT = 5;
    /** 薄弱知识点 Top N */
    private static final int WEAK_TOP_N = 8;

    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final QuestionMapper questionMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final CourseMapper courseMapper;
    private final AiProvider aiProvider;
    private final AiService aiService;
    private final LearningQuestionErrorAnalysisService questionErrorAnalysisService;
    private final SimilarQuestionRecommendationService similarQuestionRecommendationService;

    public LearningDiagnosisService(KnowledgePointMapper knowledgePointMapper,
                                     QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                     QuestionMapper questionMapper,
                                     PracticeRecordMapper practiceRecordMapper,
                                     WrongQuestionMapper wrongQuestionMapper,
                                     CourseMapper courseMapper,
                                     AiProvider aiProvider,
                                     AiService aiService,
                                     LearningQuestionErrorAnalysisService questionErrorAnalysisService,
                                     SimilarQuestionRecommendationService similarQuestionRecommendationService) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.questionMapper = questionMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.courseMapper = courseMapper;
        this.aiProvider = aiProvider;
        this.aiService = aiService;
        this.questionErrorAnalysisService = questionErrorAnalysisService;
        this.similarQuestionRecommendationService = similarQuestionRecommendationService;
    }

    /**
     * 获取学习诊断数据
     */
    @Cacheable(value = "learningDiagnosis", key = "#userId")
    public LearningDiagnosisVO getDiagnosis(Long userId) {
        log.info("生成学习诊断: userId={}", userId);

        LearningDiagnosisVO vo = new LearningDiagnosisVO();

        // 1. 获取全部练习记录
        LambdaQueryWrapper<PracticeRecord> prWrapper = new LambdaQueryWrapper<>();
        prWrapper.eq(PracticeRecord::getUserId, userId);
        List<PracticeRecord> allRecords = practiceRecordMapper.selectList(prWrapper);

        int totalPractice = allRecords.size();
        long correctCount = allRecords.stream()
                .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
        double overallRate = totalPractice == 0 ? 0
                : Math.round(correctCount * 1000.0 / totalPractice) / 10.0;

        vo.setTotalPractice(totalPractice);
        vo.setOverallCorrectRate(overallRate);
        vo.setStreakDays(calculateStreak(allRecords));
        vo.setActiveDaysLast30(calculateActiveDays(allRecords, 30));

        // 2. 获取全部错题
        LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
        wqWrapper.eq(WrongQuestion::getUserId, userId).eq(WrongQuestion::getDeleted, 0);
        List<WrongQuestion> allWrongs = wrongQuestionMapper.selectList(wqWrapper);

        // 3. 构建知识点映射
        LambdaQueryWrapper<KnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
        List<KnowledgePoint> allPoints = knowledgePointMapper.selectList(kpWrapper);
        Map<Long, Set<Long>> questionToKps = buildQuestionToKps(allPoints);

        // 4. 计算知识点薄弱诊断
        vo.setWeakPoints(computeWeakPoints(userId, allPoints, allRecords, allWrongs, questionToKps));

        // 5. 课程掌握概况
        vo.setCourseMasteries(computeCourseMasteries(allRecords, allWrongs, allPoints, questionToKps));

        // 6. 错因分析汇总
        vo.setErrorPatterns(computeErrorPatterns(allWrongs, allRecords, allPoints, questionToKps));

        // 7. 学习习惯分析
        vo.setLearningHabit(computeLearningHabit(allRecords));

        // 8. 每日推荐题目
        vo.setDailyRecommendations(computeDailyRecommendations(userId, allRecords, allWrongs, allPoints, questionToKps));

        // 9. 每日学习建议
        vo.setDailyAdvice(generateDailyAdvice(vo));

        return vo;
    }

    // ======================== 知识点薄弱诊断 ========================

    private List<LearningDiagnosisVO.WeakPoint> computeWeakPoints(
            Long userId,
            List<KnowledgePoint> allPoints,
            List<PracticeRecord> allRecords,
            List<WrongQuestion> allWrongs,
            Map<Long, Set<Long>> questionToKps) {

        // 按知识点统计练习记录
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

        // 按知识点统计错题数
        Map<Long, Integer> kpWrongCount = new HashMap<>();
        for (WrongQuestion wq : allWrongs) {
            Set<Long> kps = questionToKps.get(wq.getQuestionId());
            if (kps != null) {
                for (Long kpId : kps) {
                    kpWrongCount.merge(kpId, wq.getWrongCount(), Integer::sum);
                }
            }
        }

        // 构建课程缓存
        Map<Long, Course> courseCache = new HashMap<>();

        List<LearningDiagnosisVO.WeakPoint> weakPoints = new ArrayList<>();
        for (KnowledgePoint kp : allPoints) {
            List<PracticeRecord> records = kpRecords.getOrDefault(kp.getId(), Collections.emptyList());
            int total = records.size();
            long correct = records.stream()
                    .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
            double rate = total == 0 ? -1.0 : Math.round(correct * 1000.0 / total) / 10.0;
            int wrongs = kpWrongCount.getOrDefault(kp.getId(), 0);

            String status;
            if (total == 0) {
                status = "NOT_STARTED";
            } else if (rate >= REVIEW_THRESHOLD) {
                continue; // 掌握良好，跳过
            } else if (rate >= WEAK_THRESHOLD) {
                status = "NEEDS_REVIEW";
            } else {
                status = "WEAK";
            }

            double priority = calculatePriority(rate, total, wrongs, status);

            Course course = courseCache.computeIfAbsent(kp.getCourseId(),
                    id -> courseMapper.selectById(id));

            LearningDiagnosisVO.WeakPoint wp = new LearningDiagnosisVO.WeakPoint();
            wp.setKnowledgePointId(kp.getId());
            wp.setKnowledgePointName(kp.getName());
            wp.setCourseId(kp.getCourseId());
            wp.setCourseName(course != null ? course.getName() : "未知课程");
            wp.setCorrectRate(rate);
            wp.setTotalAttempts(total);
            wp.setWrongCount(wrongs);
            wp.setMasteryStatus(status);
            wp.setPriorityScore(priority);
            wp.setDiagnosis(generateWeakPointDiagnosis(status, rate, total, wrongs));

            weakPoints.add(wp);
        }

        // 按优先级排序，取 Top N
        weakPoints.sort((a, b) -> Double.compare(b.getPriorityScore(), a.getPriorityScore()));
        return weakPoints.stream().limit(WEAK_TOP_N).collect(Collectors.toList());
    }

    private double calculatePriority(double rate, int total, int wrongs, String status) {
        switch (status) {
            case "NOT_STARTED":
                return 60.0;
            case "WEAK":
                return 70 + (WEAK_THRESHOLD - Math.max(rate, 0)) * 0.3 + wrongs * 1.5;
            case "NEEDS_REVIEW":
                return 40 + (REVIEW_THRESHOLD - rate) * 0.5 + wrongs * 1.0;
            default:
                return 0;
        }
    }

    private String generateWeakPointDiagnosis(String status, double rate, int total, int wrongs) {
        switch (status) {
            case "NOT_STARTED":
                return "该知识点尚未开始练习，建议系统学习后进行专项练习。";
            case "WEAK":
                return String.format("正确率 %.1f%%（%d 道题中答错 %d 道），基础不扎实。建议重新学习核心概念，从基础题开始逐步提升。",
                        rate, total, wrongs);
            case "NEEDS_REVIEW":
                return String.format("正确率 %.1f%%，有一定基础但仍有薄弱环节。建议做几道变式题巩固，重点关注错题涉及的知识盲区。",
                        rate);
            default:
                return "";
        }
    }

    // ======================== 课程掌握概况 ========================

    private List<LearningDiagnosisVO.CourseMastery> computeCourseMasteries(
            List<PracticeRecord> allRecords,
            List<WrongQuestion> allWrongs,
            List<KnowledgePoint> allPoints,
            Map<Long, Set<Long>> questionToKps) {

        // 按课程统计练习
        Map<Long, List<PracticeRecord>> courseRecords = new HashMap<>();
        for (PracticeRecord r : allRecords) {
            if (r.getQuestionId() == null) continue;
            Set<Long> kps = questionToKps.get(r.getQuestionId());
            if (kps != null) {
                Set<Long> courseIds = new HashSet<>();
                for (Long kpId : kps) {
                    allPoints.stream().filter(kp -> kp.getId().equals(kpId))
                            .forEach(kp -> courseIds.add(kp.getCourseId()));
                }
                for (Long cid : courseIds) {
                    courseRecords.computeIfAbsent(cid, k -> new ArrayList<>()).add(r);
                }
            }
        }

        // 按课程统计错题
        Map<Long, Integer> courseWrongCount = new HashMap<>();
        for (WrongQuestion wq : allWrongs) {
            Set<Long> kps = questionToKps.get(wq.getQuestionId());
            if (kps != null) {
                Set<Long> courseIds = new HashSet<>();
                for (Long kpId : kps) {
                    allPoints.stream().filter(kp -> kp.getId().equals(kpId))
                            .forEach(kp -> courseIds.add(kp.getCourseId()));
                }
                for (Long cid : courseIds) {
                    courseWrongCount.merge(cid, 1, Integer::sum);
                }
            }
        }

        // 统计知识点数和薄弱知识点数
        Map<Long, Long> kpCountByCourse = allPoints.stream()
                .filter(kp -> kp.getCourseId() != null)
                .collect(Collectors.groupingBy(KnowledgePoint::getCourseId, Collectors.counting()));

        List<LearningDiagnosisVO.CourseMastery> result = new ArrayList<>();
        Map<Long, Course> courseCache = new HashMap<>();

        for (Map.Entry<Long, List<PracticeRecord>> entry : courseRecords.entrySet()) {
            Long cid = entry.getKey();
            List<PracticeRecord> records = entry.getValue();
            long correct = records.stream()
                    .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
            double rate = records.isEmpty() ? 0
                    : Math.round(correct * 1000.0 / records.size()) / 10.0;

            Course course = courseCache.computeIfAbsent(cid, id -> courseMapper.selectById(id));

            LearningDiagnosisVO.CourseMastery cm = new LearningDiagnosisVO.CourseMastery();
            cm.setCourseId(cid);
            cm.setCourseName(course != null ? course.getName() : "未知课程");
            cm.setCorrectRate(rate);
            cm.setTotalAttempts(records.size());
            cm.setWrongCount(courseWrongCount.getOrDefault(cid, 0));
            cm.setKnowledgePointCount(kpCountByCourse.getOrDefault(cid, 0L).intValue());
            // 弱项知识点数：正确率 < 70% 的知识点
            cm.setWeakPointCount(calculateWeakPointCount(cid, allPoints, allRecords, questionToKps));

            result.add(cm);
        }

        result.sort((a, b) -> Double.compare(a.getCorrectRate(), b.getCorrectRate()));
        return result;
    }

    private int calculateWeakPointCount(Long courseId, List<KnowledgePoint> allPoints,
                                         List<PracticeRecord> allRecords,
                                         Map<Long, Set<Long>> questionToKps) {
        List<KnowledgePoint> coursePoints = allPoints.stream()
                .filter(kp -> courseId.equals(kp.getCourseId()))
                .collect(Collectors.toList());

        // 按知识点统计练习
        Map<Long, List<PracticeRecord>> kpRecords = new HashMap<>();
        for (PracticeRecord r : allRecords) {
            Set<Long> kps = questionToKps.get(r.getQuestionId());
            if (kps != null) {
                for (Long kpId : kps) {
                    kpRecords.computeIfAbsent(kpId, k -> new ArrayList<>()).add(r);
                }
            }
        }

        int weakCount = 0;
        for (KnowledgePoint kp : coursePoints) {
            List<PracticeRecord> records = kpRecords.getOrDefault(kp.getId(), Collections.emptyList());
            if (records.isEmpty()) continue;
            long correct = records.stream()
                    .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
            double rate = correct * 100.0 / records.size();
            if (rate < REVIEW_THRESHOLD) weakCount++;
        }
        return weakCount;
    }

    // ======================== 错因分析 ========================

    private LearningDiagnosisVO.ErrorPatternSummary computeErrorPatterns(
            List<WrongQuestion> allWrongs, List<PracticeRecord> allRecords,
            List<KnowledgePoint> allPoints, Map<Long, Set<Long>> questionToKps) {

        LearningDiagnosisVO.ErrorPatternSummary summary = new LearningDiagnosisVO.ErrorPatternSummary();

        // 掌握程度分布
        Map<String, Integer> masteryDist = new LinkedHashMap<>();
        masteryDist.put("未掌握", (int) allWrongs.stream().filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 0).count());
        masteryDist.put("部分掌握", (int) allWrongs.stream().filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 1).count());
        masteryDist.put("已掌握", (int) allWrongs.stream().filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 2).count());
        summary.setMasteryDistribution(masteryDist);

        // 反复出错题目数（wrongCount >= 3）
        summary.setRepeatedErrorCount((int) allWrongs.stream().filter(w -> w.getWrongCount() >= 3).count());

        // 最近 7 天新增错题
        LocalDateTime weekAgo = LocalDate.now().minusDays(7).atStartOfDay();
        summary.setRecentNewWrongCount((int) allWrongs.stream()
                .filter(w -> w.getCreateTime() != null && w.getCreateTime().isAfter(weekAgo))
                .count());

        // 批量查询错题关联的题目
        List<Long> questionIds = allWrongs.stream().map(WrongQuestion::getQuestionId)
                .distinct().collect(Collectors.toList());
        Map<Long, Question> questionMap = new HashMap<>();
        if (!questionIds.isEmpty()) {
            questionMapper.selectList(new LambdaQueryWrapper<Question>().in(Question::getId, questionIds))
                    .forEach(q -> questionMap.put(q.getId(), q));
        }

        // 批量查询课程
        Set<Long> courseIds = questionMap.values().stream()
                .map(Question::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Course> courseMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            courseMapper.selectList(new LambdaQueryWrapper<Course>().in(Course::getId, courseIds))
                    .forEach(c -> courseMap.put(c.getId(), c));
        }

        // 高频错题课程
        Map<Long, Integer> courseWrongMap = new HashMap<>();
        for (WrongQuestion wq : allWrongs) {
            Question q = questionMap.get(wq.getQuestionId());
            if (q != null && q.getCourseId() != null) {
                courseWrongMap.merge(q.getCourseId(), 1, Integer::sum);
            }
        }

        List<LearningDiagnosisVO.CourseErrorCount> topCourses = courseWrongMap.entrySet().stream()
                .map(e -> {
                    LearningDiagnosisVO.CourseErrorCount cec = new LearningDiagnosisVO.CourseErrorCount();
                    cec.setCourseId(e.getKey());
                    Course c = courseMap.get(e.getKey());
                    cec.setCourseName(c != null ? c.getName() : "未知课程");
                    cec.setWrongCount(e.getValue());
                    return cec;
                })
                .sorted((a, b) -> Integer.compare(b.getWrongCount(), a.getWrongCount()))
                .limit(5)
                .collect(Collectors.toList());
        summary.setTopErrorCourses(topCourses);

        // ===== 新增：错题题型分布 =====
        Map<String, Integer> typeDist = new LinkedHashMap<>();
        for (WrongQuestion wq : allWrongs) {
            Question q = questionMap.get(wq.getQuestionId());
            if (q != null && q.getQuestionType() != null) {
                String typeName = getQuestionTypeName(q.getQuestionType());
                typeDist.merge(typeName, 1, Integer::sum);
            }
        }
        summary.setQuestionTypeDistribution(typeDist);

        // ===== 新增：错题难度分布 =====
        Map<Integer, Integer> diffDist = new LinkedHashMap<>();
        for (WrongQuestion wq : allWrongs) {
            Question q = questionMap.get(wq.getQuestionId());
            if (q != null && q.getDifficulty() != null) {
                diffDist.merge(q.getDifficulty(), 1, Integer::sum);
            }
        }
        // 按难度星级排序
        Map<Integer, Integer> sortedDiffDist = new LinkedHashMap<>();
        diffDist.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sortedDiffDist.put(e.getKey(), e.getValue()));
        summary.setDifficultyDistribution(sortedDiffDist);

        // ===== 新增：知识点错因排名（Top 8） =====
        // 按知识点统计错题数
        Map<Long, Integer> kpWrongMap = new HashMap<>();
        Map<Long, Integer> kpTotalMap = new HashMap<>();
        Map<Long, Long> kpCorrectMap = new HashMap<>();

        // 统计错题中的知识点分布
        for (WrongQuestion wq : allWrongs) {
            Set<Long> kps = questionToKps.get(wq.getQuestionId());
            if (kps != null) {
                for (Long kpId : kps) {
                    kpWrongMap.merge(kpId, wq.getWrongCount(), Integer::sum);
                }
            }
        }

        // 统计练习记录中的知识点总量和正确数
        for (PracticeRecord r : allRecords) {
            if (r.getQuestionId() == null) continue;
            Set<Long> kps = questionToKps.get(r.getQuestionId());
            if (kps != null) {
                for (Long kpId : kps) {
                    kpTotalMap.merge(kpId, 1, Integer::sum);
                    if (r.getIsCorrect() != null && r.getIsCorrect() == 1) {
                        kpCorrectMap.merge(kpId, 1L, Long::sum);
                    }
                }
            }
        }

        // 构建知识点名称缓存
        Map<Long, KnowledgePoint> kpMap = new HashMap<>();
        allPoints.forEach(kp -> kpMap.put(kp.getId(), kp));

        List<LearningDiagnosisVO.KnowledgePointErrorRank> kpErrors = kpWrongMap.entrySet().stream()
                .map(e -> {
                    LearningDiagnosisVO.KnowledgePointErrorRank rank = new LearningDiagnosisVO.KnowledgePointErrorRank();
                    rank.setKnowledgePointId(e.getKey());
                    KnowledgePoint kp = kpMap.get(e.getKey());
                    rank.setKnowledgePointName(kp != null ? kp.getName() : "未知知识点");
                    if (kp != null) {
                        rank.setCourseId(kp.getCourseId());
                        Course c = courseMap.get(kp.getCourseId());
                        if (c == null && kp.getCourseId() != null) {
                            c = courseMapper.selectById(kp.getCourseId());
                            if (c != null) courseMap.put(kp.getCourseId(), c);
                        }
                        rank.setCourseName(c != null ? c.getName() : "未知课程");
                    }
                    rank.setWrongCount(e.getValue());
                    int total = kpTotalMap.getOrDefault(e.getKey(), 0);
                    rank.setTotalAttempts(total);
                    long correct = kpCorrectMap.getOrDefault(e.getKey(), 0L);
                    double rate = total == 0 ? 0 : Math.round(correct * 1000.0 / total) / 10.0;
                    rank.setCorrectRate(rate);
                    return rank;
                })
                .sorted((a, b) -> Integer.compare(b.getWrongCount(), a.getWrongCount()))
                .limit(8)
                .collect(Collectors.toList());
        summary.setKnowledgePointErrors(kpErrors);

        // ===== 新增：反复错题详情（wrongCount >= 2，最多 10 条） =====
        List<LearningDiagnosisVO.RepeatedErrorItem> repeatedErrors = allWrongs.stream()
                .filter(w -> w.getWrongCount() >= 2)
                .sorted((a, b) -> Integer.compare(b.getWrongCount(), a.getWrongCount()))
                .limit(10)
                .map(wq -> {
                    LearningDiagnosisVO.RepeatedErrorItem item = new LearningDiagnosisVO.RepeatedErrorItem();
                    item.setQuestionId(wq.getQuestionId());
                    item.setWrongCount(wq.getWrongCount());
                    item.setMasteryLevel(wq.getMasteryLevel());
                    item.setLastWrongAnswer(wq.getLastWrongAnswer());

                    Question q = questionMap.get(wq.getQuestionId());
                    if (q != null) {
                        item.setQuestionContent(truncate(q.getContent(), 120));
                        item.setQuestionType(getQuestionTypeName(q.getQuestionType()));
                        item.setDifficulty(q.getDifficulty());
                        if (q.getCourseId() != null) {
                            Course c = courseMap.get(q.getCourseId());
                            item.setCourseName(c != null ? c.getName() : null);
                        }
                    }

                    // 知识点名称
                    Set<Long> kps = questionToKps.get(wq.getQuestionId());
                    if (kps != null && !kps.isEmpty()) {
                        KnowledgePoint kp = kpMap.get(kps.iterator().next());
                        item.setKnowledgePointName(kp != null ? kp.getName() : null);
                    }

                    return item;
                })
                .collect(Collectors.toList());
        summary.setRepeatedErrors(repeatedErrors);

        // ===== 新增：每周错题趋势（最近 4 周） =====
        List<Map<String, Object>> weeklyErrorTrend = buildWeeklyErrorTrend(allWrongs);
        summary.setWeeklyErrorTrend(weeklyErrorTrend);

        return summary;
    }

    // ======================== 学习习惯 ========================

    private LearningDiagnosisVO.LearningHabit computeLearningHabit(List<PracticeRecord> allRecords) {
        LearningDiagnosisVO.LearningHabit habit = new LearningDiagnosisVO.LearningHabit();

        if (allRecords.isEmpty()) {
            habit.setAvgDailyPractice(0);
            habit.setPreferredQuestionType("暂无数据");
            habit.setPreferredCourse("暂无数据");
            habit.setFrequencyLevel("INACTIVE");
            habit.setFrequencyDescription("暂无学习记录，开始你的第一道题吧！");
            habit.setWeeklyTrend(buildEmptyWeeklyTrend());
            return habit;
        }

        // 最近 30 天刷题
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(30).atStartOfDay();
        long recent30Count = allRecords.stream()
                .filter(r -> r.getCreateTime() != null && r.getCreateTime().isAfter(thirtyDaysAgo))
                .count();
        habit.setAvgDailyPractice(Math.round(recent30Count / 30.0 * 10.0) / 10.0);

        // 最常练习题型
        Map<String, Long> typeCount = new HashMap<>();
        for (PracticeRecord r : allRecords) {
            Question q = questionMapper.selectById(r.getQuestionId());
            if (q != null && q.getQuestionType() != null) {
                typeCount.merge(q.getQuestionType(), 1L, Long::sum);
            }
        }
        String preferredType = typeCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .map(this::getQuestionTypeName)
                .orElse("暂无数据");
        habit.setPreferredQuestionType(preferredType);

        // 最常练习课程 — 通过 question -> course
        Set<Long> allQuestionIds = allRecords.stream()
                .map(PracticeRecord::getQuestionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Question> qMap = new HashMap<>();
        if (!allQuestionIds.isEmpty()) {
            questionMapper.selectList(new LambdaQueryWrapper<Question>().in(Question::getId, allQuestionIds))
                    .forEach(q -> qMap.put(q.getId(), q));
        }
        Map<Long, Long> courseCount = new HashMap<>();
        for (PracticeRecord r : allRecords) {
            Question q = qMap.get(r.getQuestionId());
            if (q != null && q.getCourseId() != null) {
                courseCount.merge(q.getCourseId(), 1L, Long::sum);
            }
        }
        Long topCourseId = courseCount.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        if (topCourseId != null) {
            Course c = courseMapper.selectById(topCourseId);
            habit.setPreferredCourse(c != null ? c.getName() : "未知课程");
        } else {
            habit.setPreferredCourse("暂无数据");
        }

        // 学习频次
        int activeDays = calculateActiveDays(allRecords, 30);
        if (activeDays >= 20) {
            habit.setFrequencyLevel("ACTIVE");
            habit.setFrequencyDescription("近 30 天学习 " + activeDays + " 天，学习习惯很好！");
        } else if (activeDays >= 10) {
            habit.setFrequencyLevel("MODERATE");
            habit.setFrequencyDescription("近 30 天学习 " + activeDays + " 天，坚持每天练习效果更好。");
        } else {
            habit.setFrequencyLevel("INACTIVE");
            habit.setFrequencyDescription("近 30 天仅学习 " + activeDays + " 天，建议增加学习频率。");
        }

        // 最近 7 天趋势
        habit.setWeeklyTrend(buildWeeklyTrend(allRecords));

        return habit;
    }

    private List<Map<String, Object>> buildWeeklyTrend(List<PracticeRecord> allRecords) {
        LocalDate today = LocalDate.now();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        Map<String, List<PracticeRecord>> grouped = allRecords.stream()
                .filter(r -> r.getCreateTime() != null && r.getCreateTime().isAfter(weekStart))
                .collect(Collectors.groupingBy(r -> r.getCreateTime().toLocalDate().toString()));

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            List<PracticeRecord> dayRecords = grouped.getOrDefault(date, Collections.emptyList());
            long correct = dayRecords.stream()
                    .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("total", dayRecords.size());
            item.put("correct", correct);
            item.put("wrong", dayRecords.size() - correct);
            trend.add(item);
        }
        return trend;
    }

    private List<Map<String, Object>> buildEmptyWeeklyTrend() {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", today.minusDays(i).toString());
            item.put("total", 0);
            item.put("correct", 0);
            item.put("wrong", 0);
            trend.add(item);
        }
        return trend;
    }

    // ======================== 每日推荐 ========================

    private List<LearningDiagnosisVO.RecommendedQuestion> computeDailyRecommendations(
            Long userId,
            List<PracticeRecord> allRecords,
            List<WrongQuestion> allWrongs,
            List<KnowledgePoint> allPoints,
            Map<Long, Set<Long>> questionToKps) {

        List<LearningDiagnosisVO.RecommendedQuestion> recommendations = new ArrayList<>();

        // 1. 从错题中找高频错题（间隔复习：wrongCount >= 2 且 masteryLevel != 2）
        List<WrongQuestion> repeatedWrongs = allWrongs.stream()
                .filter(w -> w.getWrongCount() >= 2 && (w.getMasteryLevel() == null || w.getMasteryLevel() != 2))
                .sorted((a, b) -> Integer.compare(b.getWrongCount(), a.getWrongCount()))
                .collect(Collectors.toList());

        int count = 0;
        for (WrongQuestion wq : repeatedWrongs) {
            if (count >= RECOMMEND_COUNT) break;
            Question q = questionMapper.selectById(wq.getQuestionId());
            if (q == null) continue;

            LearningDiagnosisVO.RecommendedQuestion rq = new LearningDiagnosisVO.RecommendedQuestion();
            rq.setQuestionId(q.getId());
            rq.setReason("ERROR_PRONE");
            rq.setReasonDescription("反复出错 " + wq.getWrongCount() + " 次，建议重点复习");
            rq.setQuestionContent(truncate(q.getContent(), 100));
            rq.setQuestionType(getQuestionTypeName(q.getQuestionType()));
            rq.setDifficulty(q.getDifficulty());
            rq.setLastWrongAnswer(wq.getLastWrongAnswer());

            // 获取课程名和知识点名
            Course c = courseMapper.selectById(q.getCourseId());
            rq.setCourseName(c != null ? c.getName() : null);
            Set<Long> kps = questionToKps.get(q.getId());
            if (kps != null && !kps.isEmpty()) {
                KnowledgePoint kp = knowledgePointMapper.selectById(kps.iterator().next());
                rq.setKnowledgePointName(kp != null ? kp.getName() : null);
            }

            recommendations.add(rq);
            count++;
        }

        // 2. 如果不够，从薄弱知识点中随机挑题
        if (count < RECOMMEND_COUNT) {
            // 找到薄弱知识点
            Set<Long> weakKpIds = allPoints.stream()
                    .filter(kp -> {
                        // 简单判断：该知识点下的正确率 < 70%
                        int kpTotal = 0;
                        int kpCorrect = 0;
                        for (PracticeRecord r : allRecords) {
                            Set<Long> kps = questionToKps.get(r.getQuestionId());
                            if (kps != null && kps.contains(kp.getId())) {
                                kpTotal++;
                                if (r.getIsCorrect() != null && r.getIsCorrect() == 1) kpCorrect++;
                            }
                        }
                        return kpTotal > 0 && (kpCorrect * 100.0 / kpTotal) < 70;
                    })
                    .map(KnowledgePoint::getId)
                    .collect(Collectors.toSet());

            // 从这些知识点的题目中找未做过或答错的
            Set<Long> existingRecommendedIds = recommendations.stream()
                    .map(LearningDiagnosisVO.RecommendedQuestion::getQuestionId)
                    .collect(Collectors.toSet());

            if (weakKpIds.isEmpty()) {
                return recommendations;
            }

            LambdaQueryWrapper<QuestionKnowledgePoint> qkpWrapper = new LambdaQueryWrapper<>();
            qkpWrapper.in(QuestionKnowledgePoint::getKnowledgePointId, weakKpIds);
            List<QuestionKnowledgePoint> qkps = questionKnowledgePointMapper.selectList(qkpWrapper);

            Set<Long> candidateQIds = qkps.stream()
                    .map(QuestionKnowledgePoint::getQuestionId)
                    .filter(id -> !existingRecommendedIds.contains(id))
                    .collect(Collectors.toSet());

            Set<Long> answeredQIds = allRecords.stream()
                    .map(PracticeRecord::getQuestionId).collect(Collectors.toSet());

            // 优先推荐答错的题
            List<Long> wrongQIds = allWrongs.stream()
                    .map(WrongQuestion::getQuestionId)
                    .filter(candidateQIds::contains)
                    .collect(Collectors.toList());

            Collections.shuffle(wrongQIds);
            for (Long qId : wrongQIds) {
                if (count >= RECOMMEND_COUNT) break;
                Question q = questionMapper.selectById(qId);
                if (q == null) continue;

                LearningDiagnosisVO.RecommendedQuestion rq = new LearningDiagnosisVO.RecommendedQuestion();
                rq.setQuestionId(q.getId());
                rq.setReason("WEAK_POINT_REINFORCE");
                rq.setReasonDescription("薄弱知识点相关，建议强化练习");
                rq.setQuestionContent(truncate(q.getContent(), 100));
                rq.setQuestionType(getQuestionTypeName(q.getQuestionType()));
                rq.setDifficulty(q.getDifficulty());

                Course c = courseMapper.selectById(q.getCourseId());
                rq.setCourseName(c != null ? c.getName() : null);
                Set<Long> kps = questionToKps.get(q.getId());
                if (kps != null && !kps.isEmpty()) {
                    KnowledgePoint kp = knowledgePointMapper.selectById(kps.iterator().next());
                    rq.setKnowledgePointName(kp != null ? kp.getName() : null);
                }

                recommendations.add(rq);
                count++;
            }

            // 如果还不够，推荐未做过的题
            List<Long> untriedQIds = candidateQIds.stream()
                    .filter(id -> !answeredQIds.contains(id))
                    .collect(Collectors.toList());
            Collections.shuffle(untriedQIds);
            for (Long qId : untriedQIds) {
                if (count >= RECOMMEND_COUNT) break;
                Question q = questionMapper.selectById(qId);
                if (q == null) continue;

                LearningDiagnosisVO.RecommendedQuestion rq = new LearningDiagnosisVO.RecommendedQuestion();
                rq.setQuestionId(q.getId());
                rq.setReason("WEAK_POINT_REINFORCE");
                rq.setReasonDescription("薄弱知识点相关，尚未练习");
                rq.setQuestionContent(truncate(q.getContent(), 100));
                rq.setQuestionType(getQuestionTypeName(q.getQuestionType()));
                rq.setDifficulty(q.getDifficulty());

                Course c = courseMapper.selectById(q.getCourseId());
                rq.setCourseName(c != null ? c.getName() : null);
                Set<Long> kps = questionToKps.get(q.getId());
                if (kps != null && !kps.isEmpty()) {
                    KnowledgePoint kp = knowledgePointMapper.selectById(kps.iterator().next());
                    rq.setKnowledgePointName(kp != null ? kp.getName() : null);
                }

                recommendations.add(rq);
                count++;
            }
        }

        return recommendations;
    }

    // ======================== 每日建议 ========================

    private String generateDailyAdvice(LearningDiagnosisVO vo) {
        StringBuilder advice = new StringBuilder();

        // 根据连续天数给建议
        if (vo.getStreakDays() >= 7) {
            advice.append("🔥 连续学习 ").append(vo.getStreakDays()).append(" 天，非常好！继续保持。\n\n");
        } else if (vo.getStreakDays() >= 3) {
            advice.append("📈 连续学习 ").append(vo.getStreakDays()).append(" 天，坚持下去会更好。\n\n");
        } else if (vo.getStreakDays() == 0) {
            advice.append("💡 今天还没有开始学习，每天练习几道题效果更好。\n\n");
        }

        // 根据薄弱知识点给建议
        if (vo.getWeakPoints() != null && !vo.getWeakPoints().isEmpty()) {
            LearningDiagnosisVO.WeakPoint top = vo.getWeakPoints().get(0);
            advice.append("📚 重点关注：").append(top.getKnowledgePointName())
                    .append("（").append(top.getCourseName()).append("），正确率仅 ")
                    .append(String.format("%.1f%%", top.getCorrectRate())).append("。\n\n");
        }

        // 根据错题情况给建议
        if (vo.getErrorPatterns() != null) {
            LearningDiagnosisVO.ErrorPatternSummary ep = vo.getErrorPatterns();
            if (ep.getRepeatedErrorCount() > 0) {
                advice.append("⚠️ 有 ").append(ep.getRepeatedErrorCount())
                        .append(" 道题反复出错，建议使用 AI 讲解理解后再练习。\n\n");
            }
        }

        // 根据学习频次给建议
        if (vo.getLearningHabit() != null) {
            LearningDiagnosisVO.LearningHabit habit = vo.getLearningHabit();
            if ("INACTIVE".equals(habit.getFrequencyLevel())) {
                advice.append("⏰ ").append(habit.getFrequencyDescription()).append("\n");
            }
        }

        // 如果没有特别需要提醒的，给一个通用建议
        if (advice.length() == 0) {
            advice.append("✅ 学习状态良好，继续按计划练习吧！可以尝试更高难度的题目提升自己。");
        }

        return advice.toString().trim();
    }

    // ======================== AI 个性化学习建议 ========================

    /**
     * 生成 AI 个性化学习建议（同步）
     */
    public String generateAiAdvice(Long userId) {
        LearningDiagnosisVO diagnosis = getDiagnosis(userId);
        String systemPrompt = buildAiAdviceSystemPrompt();
        String userPrompt = buildAiAdviceUserPrompt(diagnosis);

        long start = System.currentTimeMillis();
        boolean success = false;
        String content;
        try {
            content = aiProvider.chat(systemPrompt, userPrompt);
            success = true;
            return content;
        } catch (Exception e) {
            log.error("AI 学习建议生成失败: userId={}", userId, e);
            throw e;
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            aiService.logCall(userId, "learning_advice", success, success ? null : "AI 学习建议生成失败", duration);
        }
    }

    /**
     * 生成 AI 个性化学习建议（流式）
     */
    public void generateAiAdviceStream(Long userId, java.util.function.Consumer<String> onContent) {
        LearningDiagnosisVO diagnosis = getDiagnosis(userId);
        String systemPrompt = buildAiAdviceSystemPrompt();
        String userPrompt = buildAiAdviceUserPrompt(diagnosis);

        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            aiProvider.chatStream(systemPrompt, userPrompt, onContent);
            success = true;
        } catch (Exception e) {
            log.error("AI 学习建议流式生成失败: userId={}", userId, e);
            throw e;
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            aiService.logCall(userId, "learning_advice_stream", success, success ? null : "AI 学习建议流式生成失败", duration);
        }
    }

    private String buildAiAdviceSystemPrompt() {
        return "你是一位专业的 AI 学习顾问，擅长根据学生的学习数据给出个性化、可操作的学习建议。\n\n"
                + "要求：\n"
                + "1. 根据用户的具体学习数据（薄弱知识点、错题模式、学习习惯等）给出有针对性的建议\n"
                + "2. 建议要具体可操作，不要泛泛而谈\n"
                + "3. 分析用户的学习优势和不足，给出平衡的评价\n"
                + "4. 为用户制定短期（本周）和中期（本月）学习计划\n"
                + "5. 给予鼓励，但不要过度夸奖\n"
                + "6. 使用 Markdown 格式输出，包含标题、列表等结构化内容\n"
                + "7. 回复长度控制在 500-800 字，不要太长\n"
                + "8. 使用中文回复";
    }

    private String buildAiAdviceUserPrompt(LearningDiagnosisVO diagnosis) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下学习数据，为该用户生成个性化的 AI 学习建议：\n\n");

        // 基本数据
        sb.append("## 基本学习数据\n");
        sb.append("- 总刷题数：").append(diagnosis.getTotalPractice()).append(" 道\n");
        sb.append("- 总正确率：").append(String.format("%.1f%%", diagnosis.getOverallCorrectRate())).append("\n");
        sb.append("- 连续学习天数：").append(diagnosis.getStreakDays()).append(" 天\n");
        sb.append("- 近 30 天活跃天数：").append(diagnosis.getActiveDaysLast30()).append(" 天\n\n");

        // 薄弱知识点
        if (diagnosis.getWeakPoints() != null && !diagnosis.getWeakPoints().isEmpty()) {
            sb.append("## 薄弱知识点（按优先级排序）\n");
            for (LearningDiagnosisVO.WeakPoint wp : diagnosis.getWeakPoints()) {
                sb.append("- ").append(wp.getKnowledgePointName())
                        .append("（").append(wp.getCourseName()).append("）：正确率 ")
                        .append(String.format("%.1f%%", wp.getCorrectRate()))
                        .append("，状态=").append(wp.getMasteryStatus())
                        .append("，练习 ").append(wp.getTotalAttempts()).append(" 次")
                        .append("，错 ").append(wp.getWrongCount()).append(" 题\n");
            }
            sb.append("\n");
        }

        // 课程掌握概况
        if (diagnosis.getCourseMasteries() != null && !diagnosis.getCourseMasteries().isEmpty()) {
            sb.append("## 课程掌握概况\n");
            for (LearningDiagnosisVO.CourseMastery cm : diagnosis.getCourseMasteries()) {
                sb.append("- ").append(cm.getCourseName())
                        .append("：正确率 ").append(String.format("%.1f%%", cm.getCorrectRate()))
                        .append("，练习 ").append(cm.getTotalAttempts()).append(" 次")
                        .append("，薄弱知识点 ").append(cm.getWeakPointCount()).append(" 个\n");
            }
            sb.append("\n");
        }

        // 错因分析
        if (diagnosis.getErrorPatterns() != null) {
            LearningDiagnosisVO.ErrorPatternSummary ep = diagnosis.getErrorPatterns();
            sb.append("## 错因分析\n");
            sb.append("- 反复出错题目数：").append(ep.getRepeatedErrorCount()).append(" 道\n");
            sb.append("- 近 7 天新增错题：").append(ep.getRecentNewWrongCount()).append(" 道\n");
            if (ep.getMasteryDistribution() != null) {
                sb.append("- 错题掌握程度分布：");
                ep.getMasteryDistribution().forEach((k, v) -> sb.append(k).append("=").append(v).append(" "));
                sb.append("\n");
            }
            if (ep.getTopErrorCourses() != null && !ep.getTopErrorCourses().isEmpty()) {
                sb.append("- 高频错题课程：");
                ep.getTopErrorCourses().forEach(c -> sb.append(c.getCourseName()).append("(").append(c.getWrongCount()).append(") "));
                sb.append("\n");
            }
            if (ep.getQuestionTypeDistribution() != null && !ep.getQuestionTypeDistribution().isEmpty()) {
                sb.append("- 错题题型分布：");
                ep.getQuestionTypeDistribution().forEach((k, v) -> sb.append(k).append("=").append(v).append(" "));
                sb.append("\n");
            }
            if (ep.getDifficultyDistribution() != null && !ep.getDifficultyDistribution().isEmpty()) {
                sb.append("- 错题难度分布：");
                ep.getDifficultyDistribution().forEach((k, v) -> sb.append(k).append("星=").append(v).append(" "));
                sb.append("\n");
            }
            if (ep.getKnowledgePointErrors() != null && !ep.getKnowledgePointErrors().isEmpty()) {
                sb.append("- 知识点错因排名：\n");
                ep.getKnowledgePointErrors().forEach(r -> sb.append("  · ").append(r.getKnowledgePointName())
                        .append("（").append(r.getCourseName()).append("）：错 ").append(r.getWrongCount())
                        .append(" 题，正确率 ").append(String.format("%.1f%%", r.getCorrectRate())).append("\n"));
            }
            if (ep.getWeeklyErrorTrend() != null && !ep.getWeeklyErrorTrend().isEmpty()) {
                sb.append("- 近 4 周错题趋势：");
                ep.getWeeklyErrorTrend().forEach(w -> sb.append(w.get("label")).append("=").append(w.get("count")).append(" "));
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 学习习惯
        if (diagnosis.getLearningHabit() != null) {
            LearningDiagnosisVO.LearningHabit habit = diagnosis.getLearningHabit();
            sb.append("## 学习习惯\n");
            sb.append("- 日均刷题：").append(habit.getAvgDailyPractice()).append(" 道\n");
            sb.append("- 偏好题型：").append(habit.getPreferredQuestionType()).append("\n");
            sb.append("- 偏好课程：").append(habit.getPreferredCourse()).append("\n");
            sb.append("- 学习频次：").append(habit.getFrequencyLevel())
                    .append("（").append(habit.getFrequencyDescription()).append("）\n");
            if (habit.getWeeklyTrend() != null) {
                sb.append("- 近 7 天趋势：");
                habit.getWeeklyTrend().forEach(d -> sb.append(d.get("date")).append("=").append(d.get("total")).append(" "));
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 推荐题目
        if (diagnosis.getDailyRecommendations() != null && !diagnosis.getDailyRecommendations().isEmpty()) {
            sb.append("## 今日推荐题目（").append(diagnosis.getDailyRecommendations().size()).append(" 道）\n");
            for (LearningDiagnosisVO.RecommendedQuestion rq : diagnosis.getDailyRecommendations()) {
                sb.append("- ").append(rq.getQuestionContent())
                        .append(" [").append(rq.getReasonDescription()).append("]\n");
            }
            sb.append("\n");
        }

        sb.append("请基于以上数据，给出个性化的学习建议。");
        return sb.toString();
    }

    /** 委托独立服务完成单题错因分析。 */
    public LearningDiagnosisVO.QuestionErrorAnalysis analyzeQuestionError(Long userId, Long questionId) {
        return questionErrorAnalysisService.analyzeQuestionError(userId, questionId);
    }

    /** 委托独立服务完成相似题推荐。 */
    public SimilarQuestionVO findSimilarQuestions(Long userId, Long questionId, int limit) {
        return similarQuestionRecommendationService.findSimilarQuestions(userId, questionId, limit);
    }

    // ======================== 工具方法 ========================

    private Map<Long, Set<Long>> buildQuestionToKps(List<KnowledgePoint> allPoints) {
        Set<Long> kpIds = allPoints.stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
        if (kpIds.isEmpty()) return Collections.emptyMap();

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

    private int calculateStreak(List<PracticeRecord> records) {
        if (records.isEmpty()) return 0;
        Set<String> dates = records.stream()
                .filter(r -> r.getCreateTime() != null)
                .map(r -> r.getCreateTime().toLocalDate().toString())
                .collect(Collectors.toSet());

        int streak = 0;
        LocalDate date = LocalDate.now();
        while (dates.contains(date.toString())) {
            streak++;
            date = date.minusDays(1);
        }
        if (streak == 0) {
            date = LocalDate.now().minusDays(1);
            while (dates.contains(date.toString())) {
                streak++;
                date = date.minusDays(1);
            }
        }
        return streak;
    }

    private int calculateActiveDays(List<PracticeRecord> records, int days) {
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        return (int) records.stream()
                .filter(r -> r.getCreateTime() != null && r.getCreateTime().isAfter(since))
                .map(r -> r.getCreateTime().toLocalDate())
                .distinct()
                .count();
    }

    private String getQuestionTypeName(String questionType) {
        if (questionType == null) return "未知";
        switch (questionType) {
            case "SINGLE_CHOICE": return "单选题";
            case "MULTIPLE_CHOICE": return "多选题";
            case "TRUE_FALSE": return "判断题";
            case "FILL_BLANK": return "填空题";
            case "SHORT_ANSWER": return "简答题";
            default: return questionType;
        }
    }

    /**
     * 构建每周错题趋势（最近 4 周）
     */
    private List<Map<String, Object>> buildWeeklyErrorTrend(List<WrongQuestion> allWrongs) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> trend = new ArrayList<>();

        for (int week = 3; week >= 0; week--) {
            LocalDate weekStart = today.minusWeeks(week).with(java.time.DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusDays(6);
            LocalDateTime start = weekStart.atStartOfDay();
            LocalDateTime end = weekEnd.atTime(LocalTime.MAX);

            long weekErrors = allWrongs.stream()
                    .filter(w -> w.getCreateTime() != null
                            && !w.getCreateTime().isBefore(start)
                            && !w.getCreateTime().isAfter(end))
                    .count();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("weekStart", weekStart.toString());
            item.put("weekEnd", weekEnd.toString());
            item.put("label", weekStart.getMonthValue() + "/" + weekStart.getDayOfMonth()
                    + "-" + weekEnd.getMonthValue() + "/" + weekEnd.getDayOfMonth());
            item.put("count", weekErrors);
            trend.add(item);
        }
        return trend;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
