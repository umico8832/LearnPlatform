package com.learnplatform.service;

import com.learnplatform.entity.QuestionOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnswerEvaluatorTest {

    private final AnswerEvaluator evaluator = new AnswerEvaluator();

    @Test
    void buildsSortedMultipleChoiceAnswer() {
        assertEquals("A,C", evaluator.buildCorrectAnswer(
                List.of(option("C", "C"), option("A", "A")), "MULTIPLE_CHOICE"));
    }

    @Test
    void evaluatesSupportedQuestionTypes() {
        assertTrue(evaluator.isCorrect("SINGLE_CHOICE", "a", "A"));
        assertTrue(evaluator.isCorrect("MULTIPLE_CHOICE", " c, A ", "A,C"));
        assertFalse(evaluator.isCorrect("MULTIPLE_CHOICE", "A", "A,C"));
        assertTrue(evaluator.isCorrect("TRUE_FALSE", "true", "TRUE"));
        assertTrue(evaluator.isCorrect("FILL_BLANK", " Java ", "java"));
        assertFalse(evaluator.isCorrect("SHORT_ANSWER", "answer", "answer"));
    }

    @Test
    void buildsTrueFalseAnswerFromOptionContent() {
        assertEquals("TRUE", evaluator.buildCorrectAnswer(List.of(option("A", "正确")), "TRUE_FALSE"));
        assertEquals("FALSE", evaluator.buildCorrectAnswer(List.of(option("B", "错误")), "TRUE_FALSE"));
    }

    private QuestionOption option(String label, String content) {
        QuestionOption option = new QuestionOption();
        option.setOptionLabel(label);
        option.setContent(content);
        return option;
    }
}
