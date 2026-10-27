package com.learnplatform.service;

import com.learnplatform.common.time.BusinessTime;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.dto.CourseStageAssessmentSummaryVO;
import com.learnplatform.entity.CourseStageAssessment;
import com.learnplatform.entity.CourseStageAssessmentQuestion;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.mapper.CourseLearningFactMapper;
import com.learnplatform.mapper.CourseStageAssessmentMapper;
import com.learnplatform.mapper.CourseStageAssessmentQuestionMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.service.assessment.CourseStageAssessmentKnowledgePointSummary;
import com.learnplatform.service.assessment.CourseStageAssessmentSourceComposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class CourseOverviewBatchService {
    private final CourseLearningFactMapper facts;
    private final KnowledgePointMapper knowledgePoints;
    private final CourseStageAssessmentMapper assessments;
    private final CourseStageAssessmentQuestionMapper assessmentQuestions;
    private final CourseOverviewTargetService targets;
    private final ObjectMapper objectMapper;

    public CourseOverviewBatchService(CourseLearningFactMapper facts, KnowledgePointMapper knowledgePoints,
                                      CourseStageAssessmentMapper assessments,
                                      CourseStageAssessmentQuestionMapper assessmentQuestions,
                                      CourseOverviewTargetService targets, ObjectMapper objectMapper) {
        this.facts = facts;
        this.knowledgePoints = knowledgePoints;
        this.assessments = assessments;
        this.assessmentQuestions = assessmentQuestions;
        this.targets = targets;
        this.objectMapper = objectMapper;
    }

    public Map<Long, CourseOverviewVO> getOverviews(Long userId, List<Long> courseIds) {
        return getOverviews(userId, courseIds, BusinessTime.today());
    }

    public Map<Long, CourseOverviewVO> getOverviews(Long userId, List<Long> courseIds, LocalDate today) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, CourseOverviewVO> values = facts
                .selectTotalsByCourses(userId, courseIds, today)
                .stream()
                .collect(Collectors.toMap(CourseOverviewVO::getCourseId, Function.identity()));
        for (Long id : courseIds) {
            values.computeIfAbsent(id, key -> {
                CourseOverviewVO value = new CourseOverviewVO();
                value.setCourseId(key);
                return value;
            });
        }
        Map<Long, Long> due = facts.selectFirstDueQuestions(userId, courseIds, today)
                .stream()
                .collect(Collectors.toMap(
                        CourseLearningFactMapper.CourseQuestion::getCourseId,
                        CourseLearningFactMapper.CourseQuestion::getQuestionId));
        Map<Long, Long> wrong = facts.selectFirstWrongQuestions(userId, courseIds)
                .stream()
                .collect(Collectors.toMap(
                        CourseLearningFactMapper.CourseQuestion::getCourseId,
                        CourseLearningFactMapper.CourseQuestion::getQuestionId));
        Map<Long, List<CourseOverviewVO.TutorProgressVO>> progress = facts
                .selectTutorProgressByCourses(userId, courseIds)
                .stream()
                .collect(Collectors.groupingBy(CourseLearningFactMapper.CourseTutorProgress::getCourseId,
                        Collectors.mapping(
                                this::tutorProgress,
                                Collectors.toList())));
        Map<Long, KnowledgePoint> roots = knowledgePoints.selectFirstRoots(courseIds).stream()
                .collect(Collectors.toMap(KnowledgePoint::getCourseId, Function.identity()));
        Map<Long, CourseStageAssessmentSummaryVO> latest = latestAssessments(userId, courseIds);
        for (Long id : courseIds) {
            CourseOverviewVO value = values.get(id);
            CourseOverviewTargetService.TargetSnapshot snapshot = targets.assemble(
                    value, progress.getOrDefault(id, List.of()), due.get(id), wrong.get(id), roots.get(id));
            value.setTutorProgress(snapshot.tutorProgress());
            value.setRecommendedTargets(snapshot.recommendedTargets());
            value.setLatestStageAssessment(latest.get(id));
        }
        return values;
    }

    private CourseOverviewVO.TutorProgressVO tutorProgress(CourseLearningFactMapper.CourseTutorProgress source) {
        CourseOverviewVO.TutorProgressVO value = new CourseOverviewVO.TutorProgressVO();
        value.setKnowledgePointId(source.getKnowledgePointId());
        value.setTitle(source.getTitle());
        value.setStatus(source.getStatus());
        value.setLastSessionTime(source.getLastSessionTime());
        return value;
    }

    private Map<Long, CourseStageAssessmentSummaryVO> latestAssessments(Long userId, List<Long> courseIds) {
        List<CourseStageAssessment> rows = assessments.selectLatestCompletedByCourses(
                userId, courseIds);
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(CourseStageAssessment::getId).toList();
        Map<Long, List<CourseStageAssessmentQuestion>> items = assessmentQuestions
                .selectByAssessmentIds(ids)
                .stream()
                .collect(Collectors.groupingBy(CourseStageAssessmentQuestion::getAssessmentId));
        Map<Long, CourseStageAssessmentSummaryVO> result = new HashMap<>();
        for (CourseStageAssessment assessment : rows) {
            CourseStageAssessmentSummaryVO view = new CourseStageAssessmentSummaryVO();
            view.setId(assessment.getId());
            view.setSelectionStrategy(assessment.getSelectionStrategy());
            view.setTargetKnowledgePointId(assessment.getTargetKnowledgePointId());
            view.setTargetKnowledgePointName(assessment.getTargetKnowledgePointNameSnapshot());
            view.setQuestionCount(assessment.getQuestionCount());
            view.setCorrectCount(assessment.getCorrectCount());
            view.setStartTime(assessment.getStartTime());
            view.setCompleteTime(assessment.getCompleteTime());
            List<CourseStageAssessmentQuestion> assessmentItems = items.getOrDefault(
                    assessment.getId(), List.of());
            view.setSourceComposition(CourseStageAssessmentSourceComposition.from(assessmentItems));
            view.setKnowledgePointSummary(CourseStageAssessmentKnowledgePointSummary.from(
                    assessmentItems, objectMapper));
            result.put(assessment.getCourseId(), view);
        }
        return result;
    }
}
