package com.learnplatform.service.question;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.QuestionOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void trueFalseAliasesAreNormalizedBeforeEvaluation() {
        assertEquals(List.of("TRUE", "TRUE", "TRUE", "TRUE", "FALSE", "FALSE", "FALSE", "FALSE"),
                List.of("TRUE", "正确", "对", "A", "false", "错误", "错", "b").stream()
                        .map(AutomaticGradingPolicy::normalizeTrueFalseAnswer).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MAYBE", "1", "是"})
    void invalidTrueFalseAnswerCannotBeGraded(String answer) {
        assertThrows(BusinessException.class, () -> AutomaticGradingPolicy.requireBasis("TRUE_FALSE",
                List.of(correctOption(null, answer))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SINGLE_CHOICE", "TRUE_FALSE"})
    void singleAnswerTypesRejectMultipleMarkedAnswers(String type) {
        assertThrows(BusinessException.class, () -> AutomaticGradingPolicy.requireBasis(type,
                List.of(correctOption("A", "TRUE"), correctOption("B", "FALSE"))));
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
