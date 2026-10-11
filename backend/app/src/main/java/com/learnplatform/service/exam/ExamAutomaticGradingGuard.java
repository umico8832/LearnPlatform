package com.learnplatform.service.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.service.question.AutomaticGradingPolicy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExamAutomaticGradingGuard {
    private final ExamQuestionMapper examQuestions;
    private final QuestionMapper questions;
    private final QuestionOptionMapper options;

    public ExamAutomaticGradingGuard(ExamQuestionMapper examQuestions, QuestionMapper questions,
                                    QuestionOptionMapper options) {
        this.examQuestions = examQuestions;
        this.questions = questions;
        this.options = options;
    }

    public void requirePaper(long paperId) {
        requireRelations(examQuestions.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, paperId)));
    }

    public void requireRelations(List<ExamQuestion> relations) {
        if (relations.isEmpty()) { throw invalid(); }
        for (ExamQuestion relation : relations) {
            requireQuestionId(relation.getQuestionId());
        }
    }

    public void requireQuestionIds(List<Long> questionIds) {
        for (Long questionId : questionIds) {
            requireQuestionId(questionId);
        }
    }

    public void requireQuestion(Question question, List<QuestionOption> questionOptions) {
        if (question == null || !Integer.valueOf(1).equals(question.getStatus())) {
            throw invalid();
        }
        if ("SHORT_ANSWER".equals(question.getQuestionType())) { return; }
        List<QuestionOption> correct = questionOptions.stream()
                .filter(option -> Integer.valueOf(1).equals(option.getIsCorrect()))
                .toList();
        try {
            AutomaticGradingPolicy.requireBasis(question.getQuestionType(), correct);
        } catch (BusinessException exception) {
            throw invalid();
        }
    }

    private void requireQuestionId(Long questionId) {
        if (questionId == null) { throw invalid(); }
        Question question = questions.selectById(questionId);
        if (question == null) { throw invalid(); }
        List<QuestionOption> choices = options.selectList(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, questionId));
        requireQuestion(question, choices);
    }

    private BusinessException invalid() {
        return new BusinessException(ResultCode.BUSINESS_ERROR, "试卷含无效自动判分题，请先修正题目");
    }
}
