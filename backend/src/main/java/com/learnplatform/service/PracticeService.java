package com.learnplatform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.PracticeRecordVO;
import com.learnplatform.dto.PracticeResultVO;
import com.learnplatform.dto.PracticeSubmitRequest;
import com.learnplatform.dto.QuestionVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 练习业务兼容门面，按查询、历史和判分职责转发到独立服务。 */
@Service
public class PracticeService {

    private final PracticeQuestionQueryService questionQueryService;
    private final PracticeHistoryService historyService;
    private final PracticeAnswerService answerService;

    public PracticeService(PracticeQuestionQueryService questionQueryService,
                           PracticeHistoryService historyService,
                           PracticeAnswerService answerService) {
        this.questionQueryService = questionQueryService;
        this.historyService = historyService;
        this.answerService = answerService;
    }

    public List<QuestionVO> getPracticeQuestions(Long courseId, Long knowledgePointId,
                                                  String questionType, Integer difficulty,
                                                  Integer count) {
        return questionQueryService.getPracticeQuestions(
                courseId, knowledgePointId, questionType, difficulty, count);
    }

    public PracticeResultVO submitAnswer(PracticeSubmitRequest request, Long userId) {
        return answerService.submitAnswer(request, userId);
    }

    public Page<PracticeRecordVO> getUserPracticeRecords(
            Long userId, int pageNum, int pageSize,
            String questionType, Long courseId, Integer isCorrect) {
        return historyService.getUserPracticeRecords(
                userId, pageNum, pageSize, questionType, courseId, isCorrect);
    }

    public Map<String, Object> getUserPracticeStats(Long userId) {
        return historyService.getUserPracticeStats(userId);
    }

    public List<QuestionVO> getWrongQuestionPractice(Long userId, Integer masteryLevel, Integer count) {
        return questionQueryService.getWrongQuestionPractice(userId, masteryLevel, count);
    }

    public List<QuestionVO> getFavoritePractice(Long userId, Integer count, Long questionId) {
        return questionQueryService.getFavoritePractice(userId, count, questionId);
    }
}
