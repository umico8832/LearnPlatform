package com.learnplatform.service;

import com.learnplatform.dto.ReviewScheduleVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.service.review.ReviewSchedulePolicy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 复习卡片的展示数据组装。
 */
@Service
public class ReviewScheduleCardViewService {

    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;

    public ReviewScheduleCardViewService(QuestionMapper questionMapper, CourseMapper courseMapper) {
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
    }

    public ReviewScheduleVO toView(QuestionReviewSchedule schedule) {
        List<ReviewScheduleVO> views = toViews(List.of(schedule), LocalDate.now());
        return views.isEmpty() ? null : views.getFirst();
    }

    public List<ReviewScheduleVO> toViews(List<QuestionReviewSchedule> schedules, LocalDate today) {
        if (schedules.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> questionIds = schedules.stream()
                .map(QuestionReviewSchedule::getQuestionId)
                .toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, question -> question));

        Set<Long> courseIds = questionMap.values().stream()
                .map(Question::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> courseNameMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            courseNameMap = courseMapper.selectBatchIds(courseIds).stream()
                    .collect(Collectors.toMap(Course::getId, Course::getName));
        }

        List<ReviewScheduleVO> result = new ArrayList<>();
        for (QuestionReviewSchedule schedule : schedules) {
            ReviewScheduleVO view = new ReviewScheduleVO();
            view.setId(schedule.getId());
            view.setQuestionId(schedule.getQuestionId());
            view.setEaseFactor(schedule.getEaseFactor());
            view.setIntervalDays(schedule.getIntervalDays());
            view.setRepetitions(schedule.getRepetitions());
            view.setNextReviewDate(schedule.getNextReviewDate());
            view.setLastReviewDate(schedule.getLastReviewDate());
            view.setLastQuality(schedule.getLastQuality());
            view.setTotalReviews(schedule.getTotalReviews());
            fillDueState(view, schedule, today);
            view.setStatusLabel(ReviewSchedulePolicy.statusLabel(schedule));
            fillQuestion(view, questionMap.get(schedule.getQuestionId()), courseNameMap);
            result.add(view);
        }
        return result;
    }

    private void fillDueState(ReviewScheduleVO view, QuestionReviewSchedule schedule, LocalDate today) {
        if (schedule.getNextReviewDate() == null) {
            return;
        }
        boolean overdue = schedule.getNextReviewDate().isBefore(today);
        view.setOverdue(overdue);
        if (overdue) {
            view.setOverdueDays((int) ChronoUnit.DAYS.between(schedule.getNextReviewDate(), today));
        }
    }

    private void fillQuestion(ReviewScheduleVO view, Question question, Map<Long, String> courseNameMap) {
        if (question == null) {
            return;
        }
        view.setQuestionContent(truncate(question.getContent(), 100));
        view.setQuestionType(question.getQuestionType());
        view.setDifficulty(question.getDifficulty());
        view.setCourseId(question.getCourseId());
        view.setCourseName(courseNameMap.get(question.getCourseId()));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String plain = text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        return plain.length() > maxLength ? plain.substring(0, maxLength) + "..." : plain;
    }
}
