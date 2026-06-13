package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.LearningPlanRequest;
import com.learnplatform.dto.LearningPlanVO;
import com.learnplatform.entity.LearningPlan;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.mapper.LearningPlanMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 学习计划服务
 */
@Service
public class LearningPlanService {

    private static final Logger log = LoggerFactory.getLogger(LearningPlanService.class);

    private final LearningPlanMapper learningPlanMapper;
    private final PracticeRecordMapper practiceRecordMapper;

    public LearningPlanService(LearningPlanMapper learningPlanMapper,
                               PracticeRecordMapper practiceRecordMapper) {
        this.learningPlanMapper = learningPlanMapper;
        this.practiceRecordMapper = practiceRecordMapper;
    }

    /**
     * 获取用户学习计划（含进度信息）
     */
    public LearningPlanVO getPlan(Long userId) {
        LearningPlan plan = getOrCreatePlan(userId);
        return buildVO(plan, userId);
    }

    /**
     * 更新每日目标
     */
    public LearningPlanVO updateDailyGoal(Long userId, LearningPlanRequest request) {
        LearningPlan plan = getOrCreatePlan(userId);
        plan.setDailyGoal(request.getDailyGoal());
        learningPlanMapper.updateById(plan);
        log.info("用户 {} 更新每日刷题目标为 {}", userId, request.getDailyGoal());
        return buildVO(plan, userId);
    }

    /**
     * 获取或创建默认学习计划
     */
    private LearningPlan getOrCreatePlan(Long userId) {
        LearningPlan plan = learningPlanMapper.selectOne(
                new LambdaQueryWrapper<LearningPlan>()
                        .eq(LearningPlan::getUserId, userId));
        if (plan == null) {
            plan = new LearningPlan();
            plan.setUserId(userId);
            plan.setDailyGoal(20); // 默认每天 20 题
            learningPlanMapper.insert(plan);
            log.info("为用户 {} 创建默认学习计划，每日目标 20 题", userId);
        }
        return plan;
    }

    /**
     * 构建 VO，包含今日进度、连续打卡等统计
     */
    private LearningPlanVO buildVO(LearningPlan plan, Long userId) {
        LearningPlanVO vo = new LearningPlanVO();
        vo.setDailyGoal(plan.getDailyGoal());

        // 今日已刷题数
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        Long todayCount = practiceRecordMapper.selectCount(
                new LambdaQueryWrapper<PracticeRecord>()
                        .eq(PracticeRecord::getUserId, userId)
                        .between(PracticeRecord::getCreateTime, todayStart, todayEnd));
        vo.setTodayCount(todayCount.intValue());

        // 完成百分比
        int progress = plan.getDailyGoal() > 0
                ? Math.min(100, todayCount.intValue() * 100 / plan.getDailyGoal())
                : 0;
        vo.setProgress(progress);

        // 连续打卡天数
        int streakDays = calculateStreakDays(userId);
        vo.setStreakDays(streakDays);

        // 最近一次答题日期
        PracticeRecord latestRecord = practiceRecordMapper.selectOne(
                new LambdaQueryWrapper<PracticeRecord>()
                        .eq(PracticeRecord::getUserId, userId)
                        .orderByDesc(PracticeRecord::getCreateTime)
                        .last("LIMIT 1"));
        if (latestRecord != null) {
            vo.setLastPracticeDate(latestRecord.getCreateTime()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }

        return vo;
    }

    /**
     * 计算连续打卡天数
     * 从今天开始往前数，有多少天连续有答题记录
     */
    private int calculateStreakDays(Long userId) {
        int streak = 0;
        LocalDate date = LocalDate.now();

        while (true) {
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.atTime(LocalTime.MAX);
            Long count = practiceRecordMapper.selectCount(
                    new LambdaQueryWrapper<PracticeRecord>()
                            .eq(PracticeRecord::getUserId, userId)
                            .between(PracticeRecord::getCreateTime, dayStart, dayEnd));
            if (count > 0) {
                streak++;
                date = date.minusDays(1);
            } else {
                break;
            }
            // 防止死循环，最多查 365 天
            if (streak >= 365) break;
        }
        return streak;
    }
}