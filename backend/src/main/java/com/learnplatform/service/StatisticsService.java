package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.StatisticsVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
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

    public StatisticsService(PracticeRecordMapper practiceRecordMapper,
                             WrongQuestionMapper wrongQuestionMapper,
                             QuestionMapper questionMapper,
                             CourseMapper courseMapper) {
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 获取用户学习统计
     */
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
        result.sort((a, b) -> Long.compare((long) b.get("total"), (long) a.get("total")));
        return result;
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
}