package com.learnplatform.service;

import com.learnplatform.entity.QuestionOption;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客观题答案构建与判分组件。
 */
@Component
public class AnswerEvaluator {

    public String buildCorrectAnswer(List<QuestionOption> correctOptions, String questionType) {
        if ("TRUE_FALSE".equals(questionType)) {
            if (correctOptions.isEmpty()) {
                return "";
            }
            String content = correctOptions.get(0).getContent();
            return "TRUE".equalsIgnoreCase(content) || "正确".equals(content) ? "TRUE" : "FALSE";
        }

        return correctOptions.stream()
                .map(QuestionOption::getOptionLabel)
                .sorted()
                .collect(Collectors.joining(","));
    }

    public boolean isCorrect(String questionType, String userAnswer, String correctAnswer) {
        if (userAnswer == null || correctAnswer == null) {
            return false;
        }
        if ("SINGLE_CHOICE".equals(questionType) || "TRUE_FALSE".equals(questionType)) {
            return userAnswer.trim().equalsIgnoreCase(correctAnswer.trim());
        }
        if ("MULTIPLE_CHOICE".equals(questionType)) {
            return toAnswerSet(userAnswer).equals(toAnswerSet(correctAnswer));
        }
        if ("FILL_BLANK".equals(questionType)) {
            return userAnswer.trim().equalsIgnoreCase(correctAnswer.trim());
        }
        return false;
    }

    private Set<String> toAnswerSet(String answer) {
        return Arrays.stream(answer.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }
}
