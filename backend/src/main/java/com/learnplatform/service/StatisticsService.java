package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.AdminStatisticsVO;
import com.learnplatform.dto.LearningReportVO;
import com.learnplatform.dto.StatisticsVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计服务
 */
@Service
public class StatisticsService {

    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final ExamPaperMapper examPaperMapper;
    private final ExamRecordMapper examRecordMapper;
    private final QuestionReviewScheduleMapper reviewScheduleMapper;

    public StatisticsService(PracticeRecordMapper practiceRecordMapper,
                             WrongQuestionMapper wrongQuestionMapper,
                             QuestionMapper questionMapper,
                             CourseMapper courseMapper,
                             UserMapper userMapper,
                             ExamPaperMapper examPaperMapper,
                             ExamRecordMapper examRecordMapper,
                             QuestionReviewScheduleMapper reviewScheduleMapper) {
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
        this.examPaperMapper = examPaperMapper;
        this.examRecordMapper = examRecordMapper;
        this.reviewScheduleMapper = reviewScheduleMapper;
    }

    /**
     * 获取用户学习统计
     */
    @Cacheable(value = "statistics", key = "#userId")
    public StatisticsVO getUserStatistics(Long userId) {
        StatisticsVO vo = new StatisticsVO();

        // 总刷题数
        LambdaQueryWrapper<PracticeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeRecord::getUserId, userId);
        List<PracticeRecord> allRecords = practiceRecordMapper.selectList(wrapper);
        vo.setTotalPractice(allRecords.size());

        // 答对/答错
        long correct = allRecords.stream().filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
        vo.setCorrectCount(correct);
        vo.setWrongCount(allRecords.size() - correct);
        vo.setCorrectRate(allRecords.isEmpty() ? 0 : Math.round(correct * 1000.0 / allRecords.size()) / 10.0);

        // 今日刷题
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        long today = allRecords.stream().filter(r -> r.getCreateTime() != null && r.getCreateTime().isAfter(todayStart)).count();
        vo.setTodayPractice(today);

        // 连续刷题天数
        vo.setStreakDays(calculateStreak(allRecords));

        // 错题统计
        LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
        wqWrapper.eq(WrongQuestion::getUserId, userId).eq(WrongQuestion::getDeleted, 0);
        List<WrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(wqWrapper);
        vo.setWrongQuestionCount(wrongQuestions.size());
        vo.setMasteredCount(wrongQuestions.stream().filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 2).count());

        return vo;
    }

    /**
     * 获取用户每日刷题趋势（最近 7 天）
     */
    @Cacheable(value = "dailyTrend", key = "#userId")
    public List<Map<String, Object>> getDailyTrend(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(6).atStartOfDay();

        LambdaQueryWrapper<PracticeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeRecord::getUserId, userId).ge(PracticeRecord::getCreateTime, start);
        List<PracticeRecord> records = practiceRecordMapper.selectList(wrapper);

        Map<String, List<PracticeRecord>> grouped = records.stream()
                .filter(r -> r.getCreateTime() != null)
                .collect(Collectors.groupingBy(r -> r.getCreateTime().toLocalDate().toString()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            List<PracticeRecord> dayRecords = grouped.getOrDefault(date, Collections.emptyList());
            long dayCorrect = dayRecords.stream().filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("total", dayRecords.size());
            item.put("correct", dayCorrect);
            item.put("wrong", dayRecords.size() - dayCorrect);
            result.add(item);
        }
        return result;
    }

    /**
     * 获取用户知识点正确率分布（最近做过的题目按课程统计）
     */
    @Cacheable(value = "courseStats", key = "#userId")
    public List<Map<String, Object>> getCourseStats(Long userId) {
        LambdaQueryWrapper<PracticeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeRecord::getUserId, userId);
        List<PracticeRecord> records = practiceRecordMapper.selectList(wrapper);

        // 按题目 -> 课程分组
        Map<Long, List<PracticeRecord>> byCourse = new HashMap<>();
        for (PracticeRecord r : records) {
            Question q = questionMapper.selectById(r.getQuestionId());
            if (q != null && q.getCourseId() != null) {
                byCourse.computeIfAbsent(q.getCourseId(), k -> new ArrayList<>()).add(r);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<PracticeRecord>> entry : byCourse.entrySet()) {
            Course course = courseMapper.selectById(entry.getKey());
            List<PracticeRecord> courseRecords = entry.getValue();
            long correct = courseRecords.stream().filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("courseId", entry.getKey());
            item.put("courseName", course != null ? course.getName() : "未知课程");
            item.put("total", courseRecords.size());
            item.put("correct", correct);
            item.put("correctRate", courseRecords.isEmpty() ? 0 : Math.round(correct * 1000.0 / courseRecords.size()) / 10.0);
            result.add(item);
        }
        result.sort((a, b) -> Long.compare(numberValue(b.get("total")), numberValue(a.get("total"))));
        return result;
    }

    /**
     * 获取管理端平台统计概览
     */
    @Cacheable(value = "adminStatistics", key = "'global'")
    public AdminStatisticsVO getAdminStatistics() {
        AdminStatisticsVO vo = new AdminStatisticsVO();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();

        vo.setTotalUsers(userMapper.selectCount(null));
        vo.setEnabledUsers(userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1)));
        vo.setTotalQuestions(questionMapper.selectCount(
                new LambdaQueryWrapper<Question>().eq(Question::getVisibility, "PUBLIC")));
        vo.setWeeklyNewQuestions(questionMapper.selectCount(
                new LambdaQueryWrapper<Question>().eq(Question::getVisibility, "PUBLIC")
                        .ge(Question::getCreateTime, weekStart)));
        vo.setTotalExamPapers(examPaperMapper.selectCount(
                new LambdaQueryWrapper<ExamPaper>().eq(ExamPaper::getVisibility, "PUBLIC")));
        vo.setPublishedExamPapers(examPaperMapper.selectCount(
                new LambdaQueryWrapper<ExamPaper>().eq(ExamPaper::getVisibility, "PUBLIC")
                        .eq(ExamPaper::getStatus, 1)));
        vo.setDraftExamPapers(examPaperMapper.selectCount(
                new LambdaQueryWrapper<ExamPaper>().eq(ExamPaper::getVisibility, "PUBLIC")
                        .eq(ExamPaper::getStatus, 0)));
        vo.setTotalPracticeRecords(practiceRecordMapper.selectCount(null));

        List<PracticeRecord> weeklyRecords = practiceRecordMapper.selectList(
                new LambdaQueryWrapper<PracticeRecord>()
                        .ge(PracticeRecord::getCreateTime, weekStart)
                        .orderByAsc(PracticeRecord::getCreateTime));

        long todayActiveUsers = weeklyRecords.stream()
                .filter(record -> record.getCreateTime() != null && !record.getCreateTime().isBefore(todayStart))
                .map(PracticeRecord::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        vo.setTodayActiveUsers(todayActiveUsers);

        Map<String, Long> typeDistribution = new LinkedHashMap<>();
        typeDistribution.put("单选题", countQuestionsByType("SINGLE_CHOICE"));
        typeDistribution.put("多选题", countQuestionsByType("MULTIPLE_CHOICE"));
        typeDistribution.put("判断题", countQuestionsByType("TRUE_FALSE"));
        typeDistribution.put("填空题", countQuestionsByType("FILL_BLANK"));
        typeDistribution.put("简答题", countQuestionsByType("SHORT_ANSWER"));
        vo.setQuestionTypeDistribution(typeDistribution);

        Map<LocalDate, List<PracticeRecord>> recordsByDate = weeklyRecords.stream()
                .filter(record -> record.getCreateTime() != null)
                .collect(Collectors.groupingBy(record -> record.getCreateTime().toLocalDate()));
        List<AdminStatisticsVO.DailyActivity> dailyActivity = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<PracticeRecord> dayRecords = recordsByDate.getOrDefault(date, Collections.emptyList());
            long activeUsers = dayRecords.stream()
                    .map(PracticeRecord::getUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            dailyActivity.add(new AdminStatisticsVO.DailyActivity(
                    date.toString(), dayRecords.size(), activeUsers));
        }
        vo.setDailyActivity(dailyActivity);

        return vo;
    }

    /**
     * 获取个人学习报告
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

        // 刷题量环比增长率
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

    /**
     * 构建复习统计信息并填充到 LearningReportVO
     */
    private void buildReviewStats(LearningReportVO vo, Long userId, LocalDate today, LocalDate monthStart) {
        // 1. 总卡片数
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

        // 2. 今日待复习（今天及之前到期的）
        LambdaQueryWrapper<QuestionReviewSchedule> dueWrapper = new LambdaQueryWrapper<>();
        dueWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                  .le(QuestionReviewSchedule::getNextReviewDate, today);
        Long dueCount = reviewScheduleMapper.selectCount(dueWrapper);
        vo.setDueTodayCount(dueCount != null ? dueCount.intValue() : 0);

        // 3. 本月完成复习次数（lastReviewDate 在本月范围内）
        LambdaQueryWrapper<QuestionReviewSchedule> monthReviewWrapper = new LambdaQueryWrapper<>();
        monthReviewWrapper.eq(QuestionReviewSchedule::getUserId, userId)
                          .ge(QuestionReviewSchedule::getLastReviewDate, monthStart)
                          .le(QuestionReviewSchedule::getLastReviewDate, today);
        List<QuestionReviewSchedule> monthReviewed = reviewScheduleMapper.selectList(monthReviewWrapper);
        vo.setMonthlyReviewedCount(monthReviewed.size());

        // 4. 已掌握卡片数（间隔 >= 21 天）
        List<QuestionReviewSchedule> allCards = reviewScheduleMapper.selectList(allWrapper);
        int mastered = 0;
        for (QuestionReviewSchedule card : allCards) {
            if (card.getIntervalDays() != null && card.getIntervalDays() >= 21) {
                mastered++;
            }
        }
        vo.setMasteredReviewCards(mastered);

        // 5. 连续复习天数
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

        // 6. 本月每日复习趋势（按天统计 lastReviewDate = 当天的卡片数）
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

    /**
     * 构建学习效果指标：用正确率、环比、错题转化、复习掌握和活跃天数形成可解释的综合结论。
     */
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

    // ======================== 私有方法 ========================

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
        // 如果今天没做题但昨天做了，也从昨天开始算
        if (streak == 0) {
            date = LocalDate.now().minusDays(1);
            while (dates.contains(date.toString())) {
                streak++;
                date = date.minusDays(1);
            }
        }
        return streak;
    }

    private long countQuestionsByType(String questionType) {
        return questionMapper.selectCount(
                new LambdaQueryWrapper<Question>().eq(Question::getVisibility, "PUBLIC")
                        .eq(Question::getQuestionType, questionType));
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
