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
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 聚合单题作答历史并分析错误模式与掌握趋势。 */
@Service
public class LearningQuestionErrorAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LearningQuestionErrorAnalysisService.class);

    private final QuestionMapper questionMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;

    public LearningQuestionErrorAnalysisService(
            QuestionMapper questionMapper,
            PracticeRecordMapper practiceRecordMapper,
            WrongQuestionMapper wrongQuestionMapper,
            CourseMapper courseMapper,
            KnowledgePointMapper knowledgePointMapper,
            QuestionKnowledgePointMapper questionKnowledgePointMapper) {
        this.questionMapper = questionMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
    }


    /**
     * 分析用户对某个具体题目的错误模式
     *
     * @param userId     当前用户
     * @param questionId 目标题目
     */
    public LearningDiagnosisVO.QuestionErrorAnalysis analyzeQuestionError(Long userId, Long questionId) {
        log.info("单题错因分析: userId={}, questionId={}", userId, questionId);

        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            LearningDiagnosisVO.QuestionErrorAnalysis empty = new LearningDiagnosisVO.QuestionErrorAnalysis();
            empty.setQuestionId(questionId);
            empty.setQuestionContent("题目不存在");
            empty.setAttempts(Collections.emptyList());
            return empty;
        }

        // 1. 获取用户对该题的所有练习记录（按时间正序）
        LambdaQueryWrapper<PracticeRecord> prWrapper = new LambdaQueryWrapper<>();
        prWrapper.eq(PracticeRecord::getUserId, userId)
                .eq(PracticeRecord::getQuestionId, questionId)
                .orderByAsc(PracticeRecord::getCreateTime);
        List<PracticeRecord> records = practiceRecordMapper.selectList(prWrapper);

        // 2. 获取错题本信息
        LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
        wqWrapper.eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getQuestionId, questionId)
                .eq(WrongQuestion::getDeleted, 0);
        WrongQuestion wrongQuestion = wrongQuestionMapper.selectOne(wqWrapper);

        // 3. 构建作答历史
        List<LearningDiagnosisVO.AttemptHistory> attempts = new ArrayList<>();
        for (PracticeRecord r : records) {
            LearningDiagnosisVO.AttemptHistory ah = new LearningDiagnosisVO.AttemptHistory();
            ah.setRecordId(r.getId());
            ah.setUserAnswer(r.getUserAnswer());
            ah.setIsCorrect(r.getIsCorrect());
            ah.setAnswerTime(r.getAnswerTime());
            ah.setCreateTime(r.getCreateTime() != null ? r.getCreateTime().toString() : null);
            attempts.add(ah);
        }

        // 4. 统计正确/错误次数
        int totalAttempts = records.size();
        int correctCount = (int) records.stream()
                .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
        int wrongCount = totalAttempts - correctCount;
        double correctRate = totalAttempts == 0 ? 0
                : Math.round(correctCount * 1000.0 / totalAttempts) / 10.0;

        // 5. 计算掌握趋势
        String masteryTrend = "STAGNANT";
        String trendDescription = "暂无足够数据判断趋势";
        if (totalAttempts >= 2) {
            // 取最近 5 次的正确率 vs 之前的正确率
            int recentN = Math.min(5, totalAttempts);
            List<PracticeRecord> recentRecords = records.subList(totalAttempts - recentN, totalAttempts);
            long recentCorrect = recentRecords.stream()
                    .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
            double recentRate = recentCorrect * 100.0 / recentN;

            if (totalAttempts > recentN) {
                List<PracticeRecord> earlierRecords = records.subList(0, totalAttempts - recentN);
                long earlierCorrect = earlierRecords.stream()
                        .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
                double earlierRate = earlierCorrect * 100.0 / earlierRecords.size();

                if (recentRate - earlierRate >= 20) {
                    masteryTrend = "IMPROVING";
                    trendDescription = String.format("近期正确率从 %.0f%% 提升到 %.0f%%，正在进步！",
                            earlierRate, recentRate);
                } else if (earlierRate - recentRate >= 20) {
                    masteryTrend = "DECLINING";
                    trendDescription = String.format("近期正确率从 %.0f%% 下降到 %.0f%%，需要加强复习。",
                            earlierRate, recentRate);
                } else {
                    masteryTrend = "STAGNANT";
                    trendDescription = String.format("近期正确率 %.0f%%，基本持平。", recentRate);
                }
            } else {
                if (recentRate >= 80) {
                    masteryTrend = "IMPROVING";
                    trendDescription = String.format("最近 %d 次正确率 %.0f%%，表现良好。", recentN, recentRate);
                } else if (recentRate < 50) {
                    masteryTrend = "DECLINING";
                    trendDescription = String.format("最近 %d 次正确率仅 %.0f%%，需要重点关注。", recentN, recentRate);
                } else {
                    trendDescription = String.format("最近 %d 次正确率 %.0f%%，仍需巩固。", recentN, recentRate);
                }
            }
        }

        // 6. 生成错误模式描述
        String errorPattern = buildErrorPattern(records, wrongCount, totalAttempts, wrongQuestion);

        // 7. 获取课程名和知识点名
        Course course = courseMapper.selectById(question.getCourseId());
        Set<Long> kpIds = questionToKps(questionId);
        String kpName = null;
        if (!kpIds.isEmpty()) {
            KnowledgePoint kp = knowledgePointMapper.selectById(kpIds.iterator().next());
            kpName = kp != null ? kp.getName() : null;
        }

        // 8. 组装返回
        LearningDiagnosisVO.QuestionErrorAnalysis analysis = new LearningDiagnosisVO.QuestionErrorAnalysis();
        analysis.setQuestionId(questionId);
        analysis.setQuestionContent(question.getContent());
        analysis.setQuestionType(getQuestionTypeName(question.getQuestionType()));
        analysis.setDifficulty(question.getDifficulty());
        analysis.setCourseName(course != null ? course.getName() : null);
        analysis.setKnowledgePointName(kpName);
        analysis.setTotalAttempts(totalAttempts);
        analysis.setCorrectCount(correctCount);
        analysis.setWrongCount(wrongCount);
        analysis.setCorrectRate(correctRate);
        analysis.setCurrentMasteryLevel(wrongQuestion != null ? wrongQuestion.getMasteryLevel() : null);
        analysis.setMasteryTrend(masteryTrend);
        analysis.setTrendDescription(trendDescription);
        analysis.setAttempts(attempts);
        analysis.setErrorPattern(errorPattern);

        return analysis;
    }

    private String buildErrorPattern(List<PracticeRecord> records, int wrongCount,
                                      int totalAttempts, WrongQuestion wrongQuestion) {
        if (totalAttempts == 0) {
            return "该题尚未作答。";
        }

        StringBuilder sb = new StringBuilder();

        // 是否反复出错
        if (wrongCount >= 3) {
            sb.append(String.format("⚠️ 该题已累计答错 %d 次（共作答 %d 次），属于反复错题。", wrongCount, totalAttempts));
        } else if (wrongCount >= 1) {
            sb.append(String.format("该题共作答 %d 次，答错 %d 次。", totalAttempts, wrongCount));
        } else {
            sb.append(String.format("该题共作答 %d 次，全部答对！", totalAttempts));
        }

        // 是否最近刚答错
        if (!records.isEmpty()) {
            PracticeRecord last = records.get(records.size() - 1);
            if (last.getIsCorrect() != null && last.getIsCorrect() == 0) {
                sb.append(" 最近一次作答仍然错误。");
            } else if (last.getIsCorrect() != null && last.getIsCorrect() == 1 && wrongCount > 0) {
                sb.append(" 最近一次已答对。");
            }
        }

        // 连续错误检测
        int consecutiveWrong = 0;
        for (int i = records.size() - 1; i >= 0; i--) {
            if (records.get(i).getIsCorrect() != null && records.get(i).getIsCorrect() == 0) {
                consecutiveWrong++;
            } else {
                break;
            }
        }
        if (consecutiveWrong >= 2) {
            sb.append(String.format(" 已连续答错 %d 次。", consecutiveWrong));
        }

        // 掌握程度
        if (wrongQuestion != null && wrongQuestion.getMasteryLevel() != null) {
            switch (wrongQuestion.getMasteryLevel()) {
                case 0:
                    sb.append(" 当前掌握程度：未掌握。");
                    break;
                case 1:
                    sb.append(" 当前掌握程度：部分掌握。");
                    break;
                case 2:
                    sb.append(" 当前掌握程度：已掌握。");
                    break;
                default:
                    break;
            }
        }

        return sb.toString();
    }


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
}
