package com.learnplatform.service;

import org.springframework.stereotype.Service;

@Service
public class QuestionAnswerEffectsService {
    private final WrongQuestionService wrongQuestions;
    private final SpacedRepetitionService reviews;

    public QuestionAnswerEffectsService(WrongQuestionService wrongQuestions, SpacedRepetitionService reviews) {
        this.wrongQuestions = wrongQuestions;
        this.reviews = reviews;
    }

    public void record(Long userId, Long questionId, String answer, boolean correct) {
        if (correct) { wrongQuestions.removeOnCorrect(userId, questionId); }
        else { wrongQuestions.addWrongQuestion(userId, questionId, answer); }
        reviews.addToReviewPlan(userId, questionId);
    }
}
