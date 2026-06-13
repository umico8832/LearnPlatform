package com.learnplatform.service;

import com.learnplatform.entity.QuestionOption;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 客观题答案构建与判分组件。
 *
 * <p>支持的题型判分规则：</p>
 * <ul>
 *   <li>SINGLE_CHOICE / TRUE_FALSE：忽略大小写比较</li>
 *   <li>MULTIPLE_CHOICE：选项集合忽略大小写比较</li>
 *   <li>FILL_BLANK：多空格按 | 分隔逐空比较，忽略首尾空格和大小写；
 *       单个空可配置多个可接受答案（用逗号分隔，如 "CPU,中央处理器,处理器"）</li>
 *   <li>SHORT_ANSWER：按 | 分隔的关键词匹配，用户答案中包含任意一个关键词即算正确（OR 逻辑）</li>
 * </ul>
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
            return evaluateFillBlank(userAnswer, correctAnswer);
        }
        if ("SHORT_ANSWER".equals(questionType)) {
            return evaluateShortAnswer(userAnswer, correctAnswer);
        }
        return false;
    }

    /**
     * 填空题判分。
     *
     * <p>正确答案格式：多空用 | 分隔（如 "CPU|内存|硬盘"）。
     * 单个空可包含多个可接受答案，用 , 分隔（如 "CPU,中央处理器"）。</p>
     *
     * <p>用户答案也按 | 分隔，逐空比较。</p>
     */
    boolean evaluateFillBlank(String userAnswer, String correctAnswer) {
        String[] userBlanks = userAnswer.split("\\|", -1);
        String[] correctBlanks = correctAnswer.split("\\|", -1);

        // 空数不一致时，按较短的来比较（部分作答也算部分分，但刷题场景下直接判错）
        if (userBlanks.length != correctBlanks.length) {
            return false;
        }

        for (int i = 0; i < correctBlanks.length; i++) {
            String userBlank = userBlanks[i].trim();
            String[] acceptableAnswers = correctBlanks[i].split(",");
            boolean anyMatch = Arrays.stream(acceptableAnswers)
                    .anyMatch(answer -> userBlank.equalsIgnoreCase(answer.trim()));
            if (!anyMatch) {
                return false;
            }
        }
        return true;
    }

    /**
     * 简答题判分（关键词匹配）。
     *
     * <p>正确答案格式：关键词用 | 分隔（如 "TCP|三次握手|可靠传输"）。
     * 用户答案中只要包含任意一个关键词即算正确（OR 逻辑）。</p>
     *
     * <p>匹配时忽略大小写，并对用户答案做基本的文本规范化（去除多余空白）。</p>
     */
    boolean evaluateShortAnswer(String userAnswer, String correctAnswer) {
        String normalizedUser = normalizeForComparison(userAnswer);
        if (normalizedUser.isEmpty()) {
            return false;
        }

        String[] keywords = correctAnswer.split("\\|");
        for (String keyword : keywords) {
            String trimmedKeyword = keyword.trim();
            if (!trimmedKeyword.isEmpty() && normalizedUser.contains(trimmedKeyword.toLowerCase())) {
                return true;
            }
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

    /**
     * 文本规范化：去除多余空白、首尾空白，转小写
     */
    private String normalizeForComparison(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
