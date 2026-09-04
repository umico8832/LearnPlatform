package com.learnplatform.service.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 读取智能组卷所需的候选题、知识点和用户学习事实。 */
@Service
public class AiExamCandidateLoader {

    private final QuestionMapper questionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;

    public AiExamCandidateLoader(QuestionMapper questionMapper,
                                 QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                 KnowledgePointMapper knowledgePointMapper,
                                 PracticeRecordMapper practiceRecordMapper,
                                 WrongQuestionMapper wrongQuestionMapper) {
        this.questionMapper = questionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
    }

    public List<Question> loadAvailableQuestions(Long courseId) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getStatus, 1);
        wrapper.eq(Question::getVisibility, "PUBLIC");
        if (courseId != null) {
            wrapper.eq(Question::getCourseId, courseId);
        }
        List<Question> questions = questionMapper.selectList(wrapper);
        if (questions.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "题库中没有可用的题目");
        }
        return questions;
    }

    public Map<Long, List<Long>> loadQuestionKnowledgePoints(List<Question> questions) {
        if (questions.isEmpty()) {
            return Map.of();
        }
        List<Long> questionIds = questions.stream().map(Question::getId).collect(Collectors.toList());
        LambdaQueryWrapper<QuestionKnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(QuestionKnowledgePoint::getQuestionId, questionIds);
        Map<Long, List<Long>> result = new HashMap<>();
        for (QuestionKnowledgePoint relation : questionKnowledgePointMapper.selectList(wrapper)) {
            result.computeIfAbsent(relation.getQuestionId(), ignored -> new ArrayList<>())
                    .add(relation.getKnowledgePointId());
        }
        return result;
    }

    public Map<Long, String> loadKnowledgePointNames() {
        return knowledgePointMapper.selectList(null).stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, KnowledgePoint::getName));
    }

    public Set<Long> loadUserWrongQuestionIds(Long userId) {
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId).eq(WrongQuestion::getDeleted, 0);
        return wrongQuestionMapper.selectList(wrapper).stream()
                .map(WrongQuestion::getQuestionId).collect(Collectors.toSet());
    }

    public Map<Integer, Double> loadUserDifficultyAccuracy(Long userId) {
        LambdaQueryWrapper<PracticeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeRecord::getUserId, userId);
        Map<Integer, int[]> stats = new HashMap<>();
        for (PracticeRecord record : practiceRecordMapper.selectList(wrapper)) {
            if (record.getQuestionId() == null) {
                continue;
            }
            Question question = questionMapper.selectById(record.getQuestionId());
            if (question == null || question.getDifficulty() == null) {
                continue;
            }
            int[] counts = stats.computeIfAbsent(question.getDifficulty(), ignored -> new int[]{0, 0});
            counts[1]++;
            if (record.getIsCorrect() != null && record.getIsCorrect() == 1) {
                counts[0]++;
            }
        }
        Map<Integer, Double> accuracy = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : stats.entrySet()) {
            int[] counts = entry.getValue();
            if (counts[1] > 0) {
                accuracy.put(entry.getKey(), (double) counts[0] / counts[1]);
            }
        }
        return accuracy;
    }
}
