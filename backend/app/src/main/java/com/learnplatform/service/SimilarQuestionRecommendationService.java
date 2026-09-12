package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.SimilarQuestionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.service.question.QuestionAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 根据知识点、题型、难度和课程计算相似题候选。 */
@Service
public class SimilarQuestionRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(SimilarQuestionRecommendationService.class);

    private final QuestionMapper questionMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;

    public SimilarQuestionRecommendationService(
            QuestionMapper questionMapper,
            PracticeRecordMapper practiceRecordMapper,
            CourseMapper courseMapper,
            KnowledgePointMapper knowledgePointMapper,
            QuestionKnowledgePointMapper questionKnowledgePointMapper) {
        this.questionMapper = questionMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
    }


    /**
     * 为指定错题推荐相似题目
     *
     * 相似度评分策略（满分 100）：
     * - 同知识点：+40 分
     * - 同题型：+30 分
     * - 同难度（±1）：+20 分
     * - 同课程：+10 分
     *
     * @param userId     当前用户
     * @param questionId 源题目（通常是答错的题）
     * @param limit      最多推荐数量
     */
    public SimilarQuestionVO findSimilarQuestions(Long userId, Long questionId, int limit) {
        log.info("相似题推荐: userId={}, questionId={}, limit={}", userId, questionId, limit);

        Question source = questionMapper.selectById(questionId);
        if (!QuestionAccessPolicy.canAccess(source, userId)) {
            SimilarQuestionVO empty = new SimilarQuestionVO();
            empty.setSourceQuestionId(questionId);
            empty.setSourceQuestionContent("题目不存在");
            empty.setSimilarQuestions(Collections.emptyList());
            return empty;
        }

        // 1. 获取源题目的知识点
        Set<Long> sourceKpIds = questionToKps(questionId);

        // 2. 获取用户已练习过的题目 ID
        LambdaQueryWrapper<PracticeRecord> prWrapper = new LambdaQueryWrapper<>();
        prWrapper.eq(PracticeRecord::getUserId, userId).select(PracticeRecord::getQuestionId);
        Set<Long> attemptedIds = practiceRecordMapper.selectList(prWrapper).stream()
                .map(PracticeRecord::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3. 获取所有候选题目（排除源题本身和已删除的）
        LambdaQueryWrapper<Question> qWrapper = new LambdaQueryWrapper<>();
        qWrapper.ne(Question::getId, questionId)
                .eq(Question::getStatus, 1)
                .eq(Question::getVisibility, "PUBLIC");
        List<Question> candidates = questionMapper.selectList(qWrapper);

        // 4. 获取所有知识点关联（用于批量查询）
        Set<Long> allCandidateIds = candidates.stream().map(Question::getId).collect(Collectors.toSet());
        Map<Long, Set<Long>> candidateKpsMap = new HashMap<>();
        if (!allCandidateIds.isEmpty()) {
            LambdaQueryWrapper<QuestionKnowledgePoint> qkpWrapper = new LambdaQueryWrapper<>();
            qkpWrapper.in(QuestionKnowledgePoint::getQuestionId, allCandidateIds);
            questionKnowledgePointMapper.selectList(qkpWrapper).forEach(qkp ->
                    candidateKpsMap.computeIfAbsent(qkp.getQuestionId(), k -> new HashSet<>())
                            .add(qkp.getKnowledgePointId())
            );
        }

        // 5. 获取课程名称缓存
        Map<Long, String> courseNameCache = new HashMap<>();
        Map<Long, String> kpNameCache = new HashMap<>();

        // 6. 计算相似度并排序
        List<SimilarQuestionVO.SimilarItem> items = new ArrayList<>();
        for (Question candidate : candidates) {
            int score = 0;
            List<String> reasons = new ArrayList<>();

            // 同知识点：+40
            Set<Long> candidateKpIds = candidateKpsMap.getOrDefault(candidate.getId(), Collections.emptySet());
            Set<Long> sharedKps = new HashSet<>(sourceKpIds);
            sharedKps.retainAll(candidateKpIds);
            if (!sharedKps.isEmpty()) {
                score += 40;
                reasons.add("同知识点");
            }

            // 同题型：+30
            if (source.getQuestionType() != null && source.getQuestionType().equals(candidate.getQuestionType())) {
                score += 30;
                reasons.add("同题型");
            }

            // 同难度（±1）：+20
            if (source.getDifficulty() != null && candidate.getDifficulty() != null) {
                int diff = Math.abs(source.getDifficulty() - candidate.getDifficulty());
                if (diff == 0) {
                    score += 20;
                    reasons.add("同难度");
                } else if (diff == 1) {
                    score += 10;
                    reasons.add("难度相近");
                }
            }

            // 同课程：+10
            if (source.getCourseId() != null && source.getCourseId().equals(candidate.getCourseId())) {
                score += 10;
                reasons.add("同课程");
            }

            // 仅保留有一定相似度的题目
            if (score < 30) { continue; }

            // 构建结果项
            SimilarQuestionVO.SimilarItem item = new SimilarQuestionVO.SimilarItem();
            item.setQuestionId(candidate.getId());
            item.setQuestionContent(truncate(candidate.getContent(), 100));
            item.setQuestionType(getQuestionTypeName(candidate.getQuestionType()));
            item.setDifficulty(candidate.getDifficulty());
            item.setSimilarityScore(score);
            item.setReason(String.join("、", reasons));
            item.setAlreadyAttempted(attemptedIds.contains(candidate.getId()));

            // 课程名称
            if (candidate.getCourseId() != null) {
                String cname = courseNameCache.computeIfAbsent(candidate.getCourseId(), id -> {
                    Course c = courseMapper.selectById(id);
                    return c != null ? c.getName() : null;
                });
                item.setCourseName(cname);
            }

            // 知识点名称（取第一个）
            if (!candidateKpIds.isEmpty()) {
                Long firstKpId = candidateKpIds.iterator().next();
                String kpName = kpNameCache.computeIfAbsent(firstKpId, id -> {
                    KnowledgePoint kp = knowledgePointMapper.selectById(id);
                    return kp != null ? kp.getName() : null;
                });
                item.setKnowledgePointName(kpName);
            }

            items.add(item);
        }

        // 7. 按相似度降序排序，取前 N
        items.sort((a, b) -> Integer.compare(b.getSimilarityScore(), a.getSimilarityScore()));
        List<SimilarQuestionVO.SimilarItem> topItems = items.stream().limit(limit).collect(Collectors.toList());

        // 8. 组装返回
        SimilarQuestionVO vo = new SimilarQuestionVO();
        vo.setSourceQuestionId(questionId);
        vo.setSourceQuestionContent(truncate(source.getContent(), 100));
        vo.setSimilarQuestions(topItems);
        return vo;
    }

    /**
     * 获取单个题目关联的知识点 ID 集合
     */
    private Set<Long> questionToKps(Long questionId) {
        LambdaQueryWrapper<QuestionKnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionKnowledgePoint::getQuestionId, questionId);
        return questionKnowledgePointMapper.selectList(wrapper).stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId)
                .collect(Collectors.toSet());
    }


    private String getQuestionTypeName(String questionType) {
        if (questionType == null) { return "未知"; }
        switch (questionType) {
            case "SINGLE_CHOICE": return "单选题";
            case "MULTIPLE_CHOICE": return "多选题";
            case "TRUE_FALSE": return "判断题";
            case "FILL_BLANK": return "填空题";
            case "SHORT_ANSWER": return "简答题";
            default: return questionType;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) { return null; }
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
