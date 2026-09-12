package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 加载学习诊断所需的基础事实，集中维护用户过滤和知识点关联查询。
 */
@Service
public class LearningDiagnosisDataLoader {

    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;

    public LearningDiagnosisDataLoader(KnowledgePointMapper knowledgePointMapper,
                                       QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                       PracticeRecordMapper practiceRecordMapper,
                                       WrongQuestionMapper wrongQuestionMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
    }

    /** 加载一次诊断计算共享的数据快照。 */
    public DiagnosisData load(Long userId) {
        LambdaQueryWrapper<PracticeRecord> practiceWrapper = new LambdaQueryWrapper<>();
        practiceWrapper.eq(PracticeRecord::getUserId, userId);
        List<PracticeRecord> records = practiceRecordMapper.selectList(practiceWrapper);

        LambdaQueryWrapper<WrongQuestion> wrongWrapper = new LambdaQueryWrapper<>();
        wrongWrapper.eq(WrongQuestion::getUserId, userId).eq(WrongQuestion::getDeleted, 0);
        List<WrongQuestion> wrongs = wrongQuestionMapper.selectList(wrongWrapper);

        List<KnowledgePoint> points = knowledgePointMapper.selectList(new LambdaQueryWrapper<>());
        return new DiagnosisData(records, wrongs, points, buildQuestionToKnowledgePoints(points));
    }

    private Map<Long, Set<Long>> buildQuestionToKnowledgePoints(List<KnowledgePoint> points) {
        Set<Long> knowledgePointIds = points.stream()
                .map(KnowledgePoint::getId)
                .collect(Collectors.toSet());
        if (knowledgePointIds.isEmpty()) {
            return Collections.emptyMap();
        }

        LambdaQueryWrapper<QuestionKnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(QuestionKnowledgePoint::getKnowledgePointId, knowledgePointIds);
        List<QuestionKnowledgePoint> relations = questionKnowledgePointMapper.selectList(wrapper);

        Map<Long, Set<Long>> questionToKnowledgePoints = new HashMap<>();
        for (QuestionKnowledgePoint relation : relations) {
            questionToKnowledgePoints
                    .computeIfAbsent(relation.getQuestionId(), ignored -> new HashSet<>())
                    .add(relation.getKnowledgePointId());
        }
        return questionToKnowledgePoints;
    }

    /** 学习诊断计算期间共享的基础数据。 */
    public record DiagnosisData(
            List<PracticeRecord> records,
            List<WrongQuestion> wrongs,
            List<KnowledgePoint> knowledgePoints,
            Map<Long, Set<Long>> questionToKnowledgePoints) {
    }
}
