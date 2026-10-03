package com.learnplatform.service.question;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.QuestionOption;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutomaticGradingPolicyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n\r", "\u0085\u00a0\u3000"})
    void missingOrUnicodeWhitespaceAnswerCannotBeGraded(String value) {
        for (String type : List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER")) {
            QuestionOption option = correctOption(value, value);
            assertThrows(BusinessException.class, () -> AutomaticGradingPolicy.requireBasis(type, List.of(option)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER"})
    void noCorrectOptionCannotBeGraded(String type) {
        assertThrows(BusinessException.class, () -> AutomaticGradingPolicy.requireBasis(type, List.of()));
        QuestionOption distractor = correctOption("A", "答案");
        distractor.setIsCorrect(0);
        assertThrows(BusinessException.class,
                () -> AutomaticGradingPolicy.requireBasis(type, List.of(distractor)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SINGLE_CHOICE", "MULTIPLE_CHOICE"})
    void choicesRequireTheMarkedAnswerLabel(String type) {
        assertDoesNotThrow(() -> AutomaticGradingPolicy.requireBasis(type, List.of(correctOption("A", null))));
        assertThrows(BusinessException.class,
                () -> AutomaticGradingPolicy.requireBasis(type, List.of(correctOption(null, "答案"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER"})
    void textAnswersRequireTheMarkedAnswerContent(String type) {
        assertDoesNotThrow(() -> AutomaticGradingPolicy.requireBasis(type, List.of(correctOption(null, "FALSE"))));
        assertThrows(BusinessException.class,
                () -> AutomaticGradingPolicy.requireBasis(type, List.of(correctOption("A", null))));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ESSAY", "single_choice"})
    void unsupportedTypeCannotBeGraded(String type) {
        assertThrows(BusinessException.class,
                () -> AutomaticGradingPolicy.requireBasis(type, List.of(correctOption("A", "答案"))));
    }

    private QuestionOption correctOption(String label, String content) {
        QuestionOption option = new QuestionOption();
        option.setIsCorrect(1);
        option.setOptionLabel(label);
        option.setContent(content);
        return option;
    }
}
