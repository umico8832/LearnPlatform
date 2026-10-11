package com.learnplatform.service.exam;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExamAutomaticGradingGuardTest {
    private final ExamAutomaticGradingGuard guard = new ExamAutomaticGradingGuard(null, null, null);

    @Test void rejectsDoubleCorrectSingleAndInvalidTrueFalseWithoutChangingSubjectiveBoundary() {
        assertThrows(BusinessException.class, () -> guard.requireQuestion(question("SINGLE_CHOICE"),
                List.of(option("A", "甲", 1), option("B", "乙", 1))));
        assertThrows(BusinessException.class, () -> guard.requireQuestion(question("TRUE_FALSE"),
                List.of(option("A", "正确", 1), option("B", "错误", 1))));
        assertThrows(BusinessException.class, () -> guard.requireQuestion(question("TRUE_FALSE"),
                List.of(option("A", "MAYBE", 1))));
        assertDoesNotThrow(() -> guard.requireQuestion(question("TRUE_FALSE"),
                List.of(option("B", "错误", 1))));
        assertDoesNotThrow(() -> guard.requireQuestion(question("SHORT_ANSWER"), List.of()));
    }

    private Question question(String type) {
        Question question = new Question();
        question.setQuestionType(type);
        question.setStatus(1);
        return question;
    }

    private QuestionOption option(String label, String content, int correct) {
        QuestionOption option = new QuestionOption();
        option.setOptionLabel(label);
        option.setContent(content);
        option.setIsCorrect(correct);
        return option;
    }
}
