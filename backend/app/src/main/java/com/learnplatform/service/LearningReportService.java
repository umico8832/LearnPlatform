package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.LearningReportVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.ExamRecord;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 个人学习报告聚合服务。
 */
@Service
public class LearningReportService {

    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;
    private final ExamRecordMapper examRecordMapper;
    private final QuestionReviewScheduleMapper reviewScheduleMapper;

    public LearningReportService(PracticeRecordMapper practiceRecordMapper,
                                 WrongQuestionMapper wrongQuestionMapper,
                                 QuestionMapper questionMapper,
                                 CourseMapper courseMapper,
                                 ExamRecordMapper examRecordMapper,
                                 QuestionReviewScheduleMapper reviewScheduleMapper) {
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
        this.examRecordMapper = examRecordMapper;
        this.reviewScheduleMapper = reviewScheduleMapper;
    }

    /**
     * 获取个人学习报告。
     */
    @Cacheable(value = "learningReport", key = "#userId")
    public LearningReportVO getLearningReport(Long userId) {
        LearningReportVO vo = new LearningReportVO();

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastMonthEnd = monthStart.minusDays(1);

        LocalDateTime thisMonthBegin = monthStart.atStartOfDay();
        LocalDateTime lastMonthBegin = lastMonthStart.atStartOfDay();
        LocalDateTime lastMonthEndOfDay = lastMonthEnd.atTime(LocalTime.MAX);

        // ========== 本月刷题记录 ==========
        LambdaQueryWrapper<PracticeRecord> monthWrapper = new LambdaQueryWrapper<>();
        monthWrapper.eq(PracticeRecord::getUserId, userId)
                .ge(PracticeRecord::getCreateTime, thisMonthBegin);
        List<PracticeRecord> monthRecords = practiceRecordMapper.selectList(monthWrapper);

        vo.setMonthTotalPractice(monthRecords.size());
        long monthCorrect = monthRecords.stream()
                .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
        vo.setMonthCorrectCount((int) monthCorrect);
        vo.setMonthCorrectRate(monthRecords.isEmpty() ? 0.0
                : Math.round(monthCorrect * 1000.0 / monthRecords.size()) / 10.0);

        // ========== 上月刷题记录（环比） ==========
        LambdaQueryWrapper<PracticeRecord> lastMonthWrapper = new LambdaQueryWrapper<>();
        lastMonthWrapper.eq(PracticeRecord::getUserId, userId)
                .ge(PracticeRecord::getCreateTime, lastMonthBegin)
                .le(PracticeRecord::getCreateTime, lastMonthEndOfDay);
        List<PracticeRecord> lastMonthRecords = practiceRecordMapper.selectList(lastMonthWrapper);

        vo.setLastMonthTotalPractice(lastMonthRecords.size());
        long lastMonthCorrect = lastMonthRecords.stream()
                .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
        vo.setLastMonthCorrectRate(lastMonthRecords.isEmpty() ? 0.0
                : Math.round(lastMonthCorrect * 1000.0 / lastMonthRecords.size()) / 10.0);

        if (lastMonthRecords.isEmpty()) {
            vo.setPracticeGrowthRate(monthRecords.isEmpty() ? 0.0 : 100.0);
        } else {
            double growth = (monthRecords.size() - lastMonthRecords.size()) * 100.0 / lastMonthRecords.size();
            vo.setPracticeGrowthRate(Math.round(growth * 10.0) / 10.0);
        }
        vo.setCorrectRateChange(round1(vo.getMonthCorrectRate() - vo.getLastMonthCorrectRate()));
        vo.setActiveStudyDays((int) monthRecords.stream()
                .filter(r -> r.getCreateTime() != null)
                .map(r -> r.getCreateTime().toLocalDate())
                .distinct()
                .count());

        // ========== 本月错题变化 ==========
        LambdaQueryWrapper<WrongQuestion> monthWrongWrapper = new LambdaQueryWrapper<>();
        monthWrongWrapper.eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getDeleted, 0)
                .ge(WrongQuestion::getCreateTime, thisMonthBegin);
        List<WrongQuestion> monthNewWrongs = wrongQuestionMapper.selectList(monthWrongWrapper);
        vo.setMonthNewWrongCount(monthNewWrongs.size());

        LambdaQueryWrapper<WrongQuestion> masteredWrapper = new LambdaQueryWrapper<>();
        masteredWrapper.eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getDeleted, 0)
                .eq(WrongQuestion::getMasteryLevel, 2);
        vo.setMonthMasteredCount(wrongQuestionMapper.selectCount(masteredWrapper).intValue());

        // ========== 本月考试数据 ==========
        LambdaQueryWrapper<ExamRecord> examWrapper = new LambdaQueryWrapper<>();
        examWrapper.eq(ExamRecord::getUserId, userId)
                .eq(ExamRecord::getStatus, 1)
                .ge(ExamRecord::getCreateTime, thisMonthBegin);
        List<ExamRecord> monthExams = examRecordMapper.selectList(examWrapper);

        vo.setMonthExamCount(monthExams.size());
        if (!monthExams.isEmpty()) {
            double avgScore = monthExams.stream()
                    .filter(e -> e.getScore() != null)
                    .mapToInt(ExamRecord::getScore)
                    .average()
                    .orElse(0.0);
            vo.setMonthExamAvgScore(Math.round(avgScore * 10.0) / 10.0);
        } else {
            vo.setMonthExamAvgScore(0.0);
        }

        // ========== 本月每日刷题趋势 ==========
        Map<String, List<PracticeRecord>> dailyGrouped = monthRecords.stream()
                .filter(r -> r.getCreateTime() != null)
                .collect(Collectors.groupingBy(r -> r.getCreateTime().toLocalDate().toString()));

        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        for (int i = 0; i < today.getDayOfMonth(); i++) {
            String date = monthStart.plusDays(i).toString();
            List<PracticeRecord> dayRecords = dailyGrouped.getOrDefault(date, Collections.emptyList());
            long dayCorrect = dayRecords.stream()
                    .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("total", dayRecords.size());
            item.put("correct", dayCorrect);
            item.put("wrong", dayRecords.size() - dayCorrect);
            dailyTrend.add(item);
        }
        vo.setDailyTrend(dailyTrend);

        // ========== 本月各课程正确率 ==========
        Map<Long, List<PracticeRecord>> byCourse = new HashMap<>();
        for (PracticeRecord r : monthRecords) {
            Question q = questionMapper.selectById(r.getQuestionId());
            if (q != null && q.getCourseId() != null) {
                byCourse.computeIfAbsent(q.getCourseId(), k -> new ArrayList<>()).add(r);
            }
        }
        List<Map<String, Object>> courseStats = new ArrayList<>();
        for (Map.Entry<Long, List<PracticeRecord>> entry : byCourse.entrySet()) {
            Course course = courseMapper.selectById(entry.getKey());
            List<PracticeRecord> records = entry.getValue();
            long correct = records.stream()
                    .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("courseId", entry.getKey());
            item.put("courseName", course != null ? course.getName() : "未知课程");
            item.put("total", records.size());
            item.put("correct", correct);
            item.put("correctRate", records.isEmpty() ? 0
                    : Math.round(correct * 1000.0 / records.size()) / 10.0);
            courseStats.add(item);
        }
        courseStats.sort((a, b) -> Long.compare(numberValue(b.get("total")), numberValue(a.get("total"))));
        vo.setCourseStats(courseStats);

        // ========== 本月各题型刷题分布 ==========
        Map<String, Integer> typeDist = new LinkedHashMap<>();
        for (PracticeRecord r : monthRecords) {
            Question q = questionMapper.selectById(r.getQuestionId());
            if (q != null && q.getQuestionType() != null) {
                String typeName = getQuestionTypeName(q.getQuestionType());
                typeDist.merge(typeName, 1, Integer::sum);
            }
        }
        vo.setQuestionTypeDistribution(typeDist);

        // ========== 复习统计（间隔重复） ==========
        buildReviewStats(vo, userId, today, monthStart);
        buildLearningEffectMetrics(vo, today);

        return vo;
    }

    private void buildReviewStats(LearningReportVO vo, Long userId, LocalDate today, LocalDate monthStart) {
        LambdaQueryWrapper<QuestionReviewSchedule> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.eq(QuestionReviewSchedule::getUserId, userId);
        long totalCards = reviewScheduleMapper.selectCount(allWrapper);
        vo.setTotalReviewCards((int) totalCards);

        if (totalCards == 0) {
            vo.setMonthlyReviewedCount(0);
            vo.setReviewStreakDays(0);
            vo.setMasteredReviewCards(0);
            vo.setDueTodayCount(0);
            vo.setMonthlyReviewTrend(Collections.emptyList());
            return;
        }

        LambdaQueryWrapper<QuestionReviewSchedule> dueWrapper = new LambdaQueryWrapper<>();
        dueWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                .le(QuestionReviewSchedule::getNextReviewDate, today);
        Long dueCount = reviewScheduleMapper.selectCount(dueWrapper);
        vo.setDueTodayCount(dueCount != null ? dueCount.intValue() : 0);

        LambdaQueryWrapper<QuestionReviewSchedule> monthReviewWrapper = new LambdaQueryWrapper<>();
        monthReviewWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                .ge(QuestionReviewSchedule::getLastReviewDate, monthStart)
                .le(QuestionReviewSchedule::getLastReviewDate, today);
        List<QuestionReviewSchedule> monthReviewed = reviewScheduleMapper.selectList(monthReviewWrapper);
        vo.setMonthlyReviewedCount(monthReviewed.size());

        List<QuestionReviewSchedule> allCards = reviewScheduleMapper.selectList(allWrapper);
        int mastered = 0;
        for (QuestionReviewSchedule card : allCards) {
            if (card.getIntervalDays() != null && card.getIntervalDays() >= 21) {
                mastered++;
            }
        }
        vo.setMasteredReviewCards(mastered);

        int streak = 0;
        LocalDate checkDate = today;
        while (true) {
            LambdaQueryWrapper<QuestionReviewSchedule> streakWrapper = new LambdaQueryWrapper<>();
            streakWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                    .eq(QuestionReviewSchedule::getLastReviewDate, checkDate);
            if (reviewScheduleMapper.selectCount(streakWrapper) > 0) {
                streak++;
                checkDate = checkDate.minusDays(1);
            } else {
                break;
            }
        }
        vo.setReviewStreakDays(streak);

        List<Integer> monthlyTrend = new ArrayList<>();
        for (int i = 0; i < today.getDayOfMonth(); i++) {
            LocalDate d = monthStart.plusDays(i);
            long dayCount = monthReviewed.stream()
                    .filter(s -> d.equals(s.getLastReviewDate()))
                    .count();
            monthlyTrend.add((int) dayCount);
        }
        vo.setMonthlyReviewTrend(monthlyTrend);
    }

    private void buildLearningEffectMetrics(LearningReportVO vo, LocalDate today) {
        int newWrongCount = defaultInt(vo.getMonthNewWrongCount());
        int masteredWrongCount = defaultInt(vo.getMonthMasteredCount());
        int wrongBase = newWrongCount + masteredWrongCount;
        double wrongConversionRate = wrongBase == 0 ? 0.0 : masteredWrongCount * 100.0 / wrongBase;
        vo.setWrongQuestionConversionRate(round1(wrongConversionRate));

        int totalReviewCards = defaultInt(vo.getTotalReviewCards());
        int masteredReviewCards = defaultInt(vo.getMasteredReviewCards());
        double reviewMasteryRate = totalReviewCards == 0 ? 0.0 : masteredReviewCards * 100.0 / totalReviewCards;
        vo.setReviewMasteryRate(round1(reviewMasteryRate));

        double activityRate = today.getDayOfMonth() == 0 ? 0.0
                : defaultInt(vo.getActiveStudyDays()) * 100.0 / today.getDayOfMonth();
        double trendScore = clamp(50.0 + defaultDouble(vo.getCorrectRateChange()) * 2.0, 0.0, 100.0);
        double score = defaultDouble(vo.getMonthCorrectRate()) * 0.45
                + trendScore * 0.20
                + wrongConversionRate * 0.15
                + reviewMasteryRate * 0.10
                + activityRate * 0.10;
        vo.setLearningEffectScore(round1(score));

        if (vo.getLearningEffectScore() >= 85.0) {
            vo.setLearningEffectLevel("EXCELLENT");
            vo.setLearningEffectLabel("效果优秀");
            vo.setLearningEffectSummary("本月正确率、错题转化和复习掌握都处于较好状态，可以继续增加综合题和考试训练。");
        } else if (vo.getLearningEffectScore() >= 70.0) {
            vo.setLearningEffectLevel("IMPROVING");
            vo.setLearningEffectLabel("稳步提升");
            vo.setLearningEffectSummary("学习效果正在提升，建议保持当前节奏，并优先复盘新增错题。");
        } else if (vo.getLearningEffectScore() >= 50.0) {
            vo.setLearningEffectLevel("STABLE");
            vo.setLearningEffectLabel("基本稳定");
            vo.setLearningEffectSummary("本月有学习沉淀，但提升不够明显，建议增加间隔复习和薄弱课程专项练习。");
        } else {
            vo.setLearningEffectLevel("AT_RISK");
            vo.setLearningEffectLabel("需要关注");
            vo.setLearningEffectSummary("当前学习效果偏弱，建议先恢复稳定刷题频率，并集中处理未掌握错题。");
        }
    }

    private String getQuestionTypeName(String questionType) {
        if (questionType == null) {
            return "未知";
        }
        switch (questionType) {
            case "SINGLE_CHOICE": return "单选题";
            case "MULTIPLE_CHOICE": return "多选题";
            case "TRUE_FALSE": return "判断题";
            case "FILL_BLANK": return "填空题";
            case "SHORT_ANSWER": return "简答题";
            default: return questionType;
        }
    }

    private static long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static int defaultInt(Integer value) {
        return value != null ? value : 0;
    }

    private static double defaultDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
