package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds daily question recommendations while enforcing question visibility. */
@Service
public class LearningDiagnosisRecommendationService {

    private static final int RECOMMEND_COUNT = 5;

    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    public LearningDiagnosisRecommendationService(QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                                   QuestionMapper questionMapper,
                                                   CourseMapper courseMapper,
                                                   KnowledgePointMapper knowledgePointMapper) {
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
    }

    List<LearningDiagnosisVO.RecommendedQuestion> recommend(
            Long userId,
            List<PracticeRecord> allRecords,
            List<WrongQuestion> allWrongs,
            List<KnowledgePoint> allPoints,
            Map<Long, Set<Long>> questionToKps) {
        List<LearningDiagnosisVO.RecommendedQuestion> recommendations = new ArrayList<>();

        List<WrongQuestion> repeatedWrongs = allWrongs.stream()
                .filter(w -> w.getWrongCount() >= 2 && (w.getMasteryLevel() == null || w.getMasteryLevel() != 2))
                .sorted((a, b) -> Integer.compare(b.getWrongCount(), a.getWrongCount()))
                .toList();

        for (WrongQuestion wrong : repeatedWrongs) {
            if (recommendations.size() >= RECOMMEND_COUNT) { break; }
            Question question = questionMapper.selectById(wrong.getQuestionId());
            if (!isEnabledAndAccessible(question, userId)) { continue; }
            recommendations.add(toRecommendation(question, questionToKps, "ERROR_PRONE",
                    "反复出错 " + wrong.getWrongCount() + " 次，建议重点复习", wrong.getLastWrongAnswer()));
        }

        if (recommendations.size() >= RECOMMEND_COUNT) { return recommendations; }

        Set<Long> weakKnowledgePointIds = findWeakKnowledgePointIds(allRecords, allPoints, questionToKps);
        if (weakKnowledgePointIds.isEmpty()) { return recommendations; }

        Set<Long> existingIds = recommendations.stream()
                .map(LearningDiagnosisVO.RecommendedQuestion::getQuestionId)
                .collect(Collectors.toSet());
        List<QuestionKnowledgePoint> links = questionKnowledgePointMapper.selectList(
                new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .in(QuestionKnowledgePoint::getKnowledgePointId, weakKnowledgePointIds));
        Set<Long> candidateIds = links.stream()
                .map(QuestionKnowledgePoint::getQuestionId)
                .filter(id -> !existingIds.contains(id))
                .collect(Collectors.toSet());
        Set<Long> answeredIds = allRecords.stream()
                .map(PracticeRecord::getQuestionId)
                .collect(Collectors.toSet());

        List<Long> wrongCandidateIds = allWrongs.stream()
                .map(WrongQuestion::getQuestionId)
                .filter(candidateIds::contains)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(wrongCandidateIds);
        addCandidates(recommendations, wrongCandidateIds, userId, questionToKps,
                "薄弱知识点相关，建议强化练习");

        List<Long> untriedIds = candidateIds.stream()
                .filter(id -> !answeredIds.contains(id))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(untriedIds);
        addCandidates(recommendations, untriedIds, userId, questionToKps,
                "薄弱知识点相关，尚未练习");
        return recommendations;
    }

    private Set<Long> findWeakKnowledgePointIds(List<PracticeRecord> records,
                                                 List<KnowledgePoint> points,
                                                 Map<Long, Set<Long>> questionToKps) {
        Set<Long> weakIds = new HashSet<>();
        for (KnowledgePoint point : points) {
            int total = 0;
            int correct = 0;
            for (PracticeRecord record : records) {
                Set<Long> knowledgePointIds = questionToKps.get(record.getQuestionId());
                if (knowledgePointIds != null && knowledgePointIds.contains(point.getId())) {
                    total++;
                    if (record.getIsCorrect() != null && record.getIsCorrect() == 1) { correct++; }
                }
            }
            if (total > 0 && correct * 100.0 / total < 70) { weakIds.add(point.getId()); }
        }
        return weakIds;
    }

    private void addCandidates(List<LearningDiagnosisVO.RecommendedQuestion> recommendations,
                               List<Long> candidateIds,
                               Long userId,
                               Map<Long, Set<Long>> questionToKps,
                               String description) {
        for (Long questionId : candidateIds) {
            if (recommendations.size() >= RECOMMEND_COUNT) { break; }
            Question question = questionMapper.selectById(questionId);
            if (!isEnabledAndAccessible(question, userId)) { continue; }
            recommendations.add(toRecommendation(question, questionToKps,
                    "WEAK_POINT_REINFORCE", description, null));
        }
    }

    private boolean isEnabledAndAccessible(Question question, Long userId) {
        return question != null && Integer.valueOf(1).equals(question.getStatus())
                && QuestionAccessPolicy.canAccess(question, userId);
    }

    private LearningDiagnosisVO.RecommendedQuestion toRecommendation(
            Question question,
            Map<Long, Set<Long>> questionToKps,
            String reason,
            String description,
            String lastWrongAnswer) {
        LearningDiagnosisVO.RecommendedQuestion recommendation = new LearningDiagnosisVO.RecommendedQuestion();
        recommendation.setQuestionId(question.getId());
        recommendation.setReason(reason);
        recommendation.setReasonDescription(description);
        recommendation.setQuestionContent(truncate(question.getContent(), 100));
        recommendation.setQuestionType(questionTypeName(question.getQuestionType()));
        recommendation.setDifficulty(question.getDifficulty());
        recommendation.setLastWrongAnswer(lastWrongAnswer);

        Course course = courseMapper.selectById(question.getCourseId());
        recommendation.setCourseName(course != null ? course.getName() : null);
        Set<Long> knowledgePointIds = questionToKps.get(question.getId());
        if (knowledgePointIds != null && !knowledgePointIds.isEmpty()) {
            KnowledgePoint point = knowledgePointMapper.selectById(knowledgePointIds.iterator().next());
            recommendation.setKnowledgePointName(point != null ? point.getName() : null);
        }
        return recommendation;
    }

    private String questionTypeName(String questionType) {
        if (questionType == null) { return "未知"; }
        return switch (questionType) {
            case "SINGLE_CHOICE" -> "单选题";
            case "MULTIPLE_CHOICE" -> "多选题";
            case "TRUE_FALSE" -> "判断题";
            case "FILL_BLANK" -> "填空题";
            case "SHORT_ANSWER" -> "简答题";
            default -> questionType;
        };
    }

    private String truncate(String text, int maxLength) {
        if (text == null) { return null; }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
