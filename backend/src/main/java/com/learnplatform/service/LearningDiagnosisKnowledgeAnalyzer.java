package com.learnplatform.service;

import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 计算薄弱知识点和课程掌握度。 */
@Service
public class LearningDiagnosisKnowledgeAnalyzer {

    private static final double WEAK_THRESHOLD = 50.0;
    private static final double REVIEW_THRESHOLD = 70.0;
    private static final int WEAK_TOP_N = 8;

    private final CourseMapper courseMapper;

    public LearningDiagnosisKnowledgeAnalyzer(CourseMapper courseMapper) {
        this.courseMapper = courseMapper;
    }

    public List<LearningDiagnosisVO.WeakPoint> computeWeakPoints(
            List<KnowledgePoint> allPoints,
            List<PracticeRecord> allRecords,
            List<WrongQuestion> allWrongs,
            Map<Long, Set<Long>> questionToKps) {

        Map<Long, List<PracticeRecord>> kpRecords = new HashMap<>();
        for (PracticeRecord record : allRecords) {
            if (record.getQuestionId() == null) {
                continue;
            }
            Set<Long> knowledgePointIds = questionToKps.get(record.getQuestionId());
            if (knowledgePointIds != null) {
                for (Long knowledgePointId : knowledgePointIds) {
                    kpRecords.computeIfAbsent(knowledgePointId, ignored -> new ArrayList<>()).add(record);
                }
            }
        }

        Map<Long, Integer> kpWrongCount = new HashMap<>();
        for (WrongQuestion wrong : allWrongs) {
            Set<Long> knowledgePointIds = questionToKps.get(wrong.getQuestionId());
            if (knowledgePointIds != null) {
                for (Long knowledgePointId : knowledgePointIds) {
                    kpWrongCount.merge(knowledgePointId, wrong.getWrongCount(), Integer::sum);
                }
            }
        }

        Map<Long, Course> courseCache = new HashMap<>();
        List<LearningDiagnosisVO.WeakPoint> weakPoints = new ArrayList<>();
        for (KnowledgePoint point : allPoints) {
            List<PracticeRecord> records = kpRecords.getOrDefault(point.getId(), Collections.emptyList());
            int total = records.size();
            long correct = records.stream()
                    .filter(record -> record.getIsCorrect() != null && record.getIsCorrect() == 1)
                    .count();
            double rate = total == 0 ? -1.0 : Math.round(correct * 1000.0 / total) / 10.0;
            int wrongs = kpWrongCount.getOrDefault(point.getId(), 0);

            String status;
            if (total == 0) {
                status = "NOT_STARTED";
            } else if (rate >= REVIEW_THRESHOLD) {
                continue;
            } else if (rate >= WEAK_THRESHOLD) {
                status = "NEEDS_REVIEW";
            } else {
                status = "WEAK";
            }

            Course course = courseCache.computeIfAbsent(point.getCourseId(), courseMapper::selectById);
            LearningDiagnosisVO.WeakPoint weakPoint = new LearningDiagnosisVO.WeakPoint();
            weakPoint.setKnowledgePointId(point.getId());
            weakPoint.setKnowledgePointName(point.getName());
            weakPoint.setCourseId(point.getCourseId());
            weakPoint.setCourseName(course != null ? course.getName() : "未知课程");
            weakPoint.setCorrectRate(rate);
            weakPoint.setTotalAttempts(total);
            weakPoint.setWrongCount(wrongs);
            weakPoint.setMasteryStatus(status);
            weakPoint.setPriorityScore(calculatePriority(rate, wrongs, status));
            weakPoint.setDiagnosis(generateDiagnosis(status, rate, total, wrongs));
            weakPoints.add(weakPoint);
        }

        weakPoints.sort((left, right) -> Double.compare(right.getPriorityScore(), left.getPriorityScore()));
        return weakPoints.stream().limit(WEAK_TOP_N).collect(Collectors.toList());
    }

    public List<LearningDiagnosisVO.CourseMastery> computeCourseMasteries(
            List<PracticeRecord> allRecords,
            List<WrongQuestion> allWrongs,
            List<KnowledgePoint> allPoints,
            Map<Long, Set<Long>> questionToKps) {

        Map<Long, Set<Long>> knowledgePointCourses = buildKnowledgePointCourses(allPoints);
        Map<Long, List<PracticeRecord>> courseRecords = new HashMap<>();
        for (PracticeRecord record : allRecords) {
            Set<Long> courseIds = resolveCourseIds(record.getQuestionId(), questionToKps, knowledgePointCourses);
            for (Long courseId : courseIds) {
                courseRecords.computeIfAbsent(courseId, ignored -> new ArrayList<>()).add(record);
            }
        }

        Map<Long, Integer> courseWrongCount = new HashMap<>();
        for (WrongQuestion wrong : allWrongs) {
            Set<Long> courseIds = resolveCourseIds(wrong.getQuestionId(), questionToKps, knowledgePointCourses);
            for (Long courseId : courseIds) {
                courseWrongCount.merge(courseId, 1, Integer::sum);
            }
        }

        Map<Long, Long> knowledgePointCountByCourse = allPoints.stream()
                .filter(point -> point.getCourseId() != null)
                .collect(Collectors.groupingBy(KnowledgePoint::getCourseId, Collectors.counting()));
        Map<Long, Course> courseCache = new HashMap<>();
        List<LearningDiagnosisVO.CourseMastery> result = new ArrayList<>();

        for (Map.Entry<Long, List<PracticeRecord>> entry : courseRecords.entrySet()) {
            Long courseId = entry.getKey();
            List<PracticeRecord> records = entry.getValue();
            long correct = records.stream()
                    .filter(record -> record.getIsCorrect() != null && record.getIsCorrect() == 1)
                    .count();
            double rate = records.isEmpty() ? 0 : Math.round(correct * 1000.0 / records.size()) / 10.0;
            Course course = courseCache.computeIfAbsent(courseId, courseMapper::selectById);

            LearningDiagnosisVO.CourseMastery mastery = new LearningDiagnosisVO.CourseMastery();
            mastery.setCourseId(courseId);
            mastery.setCourseName(course != null ? course.getName() : "未知课程");
            mastery.setCorrectRate(rate);
            mastery.setTotalAttempts(records.size());
            mastery.setWrongCount(courseWrongCount.getOrDefault(courseId, 0));
            mastery.setKnowledgePointCount(knowledgePointCountByCourse.getOrDefault(courseId, 0L).intValue());
            mastery.setWeakPointCount(calculateWeakPointCount(courseId, allPoints, allRecords, questionToKps));
            result.add(mastery);
        }

        result.sort((left, right) -> Double.compare(left.getCorrectRate(), right.getCorrectRate()));
        return result;
    }

    private Map<Long, Set<Long>> buildKnowledgePointCourses(List<KnowledgePoint> allPoints) {
        Map<Long, Set<Long>> result = new HashMap<>();
        for (KnowledgePoint point : allPoints) {
            result.computeIfAbsent(point.getId(), ignored -> new HashSet<>()).add(point.getCourseId());
        }
        return result;
    }

    private Set<Long> resolveCourseIds(Long questionId, Map<Long, Set<Long>> questionToKps,
                                       Map<Long, Set<Long>> knowledgePointCourses) {
        Set<Long> result = new HashSet<>();
        Set<Long> knowledgePointIds = questionToKps.get(questionId);
        if (knowledgePointIds == null) {
            return result;
        }
        for (Long knowledgePointId : knowledgePointIds) {
            result.addAll(knowledgePointCourses.getOrDefault(knowledgePointId, Collections.emptySet()));
        }
        return result;
    }

    private int calculateWeakPointCount(Long courseId, List<KnowledgePoint> allPoints,
                                        List<PracticeRecord> allRecords,
                                        Map<Long, Set<Long>> questionToKps) {
        Set<Long> coursePointIds = allPoints.stream()
                .filter(point -> courseId.equals(point.getCourseId()))
                .map(KnowledgePoint::getId)
                .collect(Collectors.toSet());
        Map<Long, List<PracticeRecord>> pointRecords = new HashMap<>();
        for (PracticeRecord record : allRecords) {
            Set<Long> knowledgePointIds = questionToKps.get(record.getQuestionId());
            if (knowledgePointIds != null) {
                for (Long knowledgePointId : knowledgePointIds) {
                    pointRecords.computeIfAbsent(knowledgePointId, ignored -> new ArrayList<>()).add(record);
                }
            }
        }

        int weakCount = 0;
        for (Long pointId : coursePointIds) {
            List<PracticeRecord> records = pointRecords.getOrDefault(pointId, Collections.emptyList());
            if (records.isEmpty()) {
                continue;
            }
            long correct = records.stream()
                    .filter(record -> record.getIsCorrect() != null && record.getIsCorrect() == 1)
                    .count();
            if (correct * 100.0 / records.size() < REVIEW_THRESHOLD) {
                weakCount++;
            }
        }
        return weakCount;
    }

    private double calculatePriority(double rate, int wrongs, String status) {
        return switch (status) {
            case "NOT_STARTED" -> 60.0;
            case "WEAK" -> 70 + (WEAK_THRESHOLD - Math.max(rate, 0)) * 0.3 + wrongs * 1.5;
            case "NEEDS_REVIEW" -> 40 + (REVIEW_THRESHOLD - rate) * 0.5 + wrongs;
            default -> 0;
        };
    }

    private String generateDiagnosis(String status, double rate, int total, int wrongs) {
        return switch (status) {
            case "NOT_STARTED" -> "该知识点尚未开始练习，建议系统学习后进行专项练习。";
            case "WEAK" -> String.format(
                    "正确率 %.1f%%（%d 道题中答错 %d 道），基础不扎实。建议重新学习核心概念，从基础题开始逐步提升。",
                    rate, total, wrongs);
            case "NEEDS_REVIEW" -> String.format(
                    "正确率 %.1f%%，有一定基础但仍有薄弱环节。建议做几道变式题巩固，重点关注错题涉及的知识盲区。", rate);
            default -> "";
        };
    }
}
