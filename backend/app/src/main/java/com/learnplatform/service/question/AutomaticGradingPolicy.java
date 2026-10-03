package com.learnplatform.service.question;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.QuestionReviewSchedule;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AutomaticGradingPolicy {
    private static final List<String> SUPPORTED_TYPES = List.of(
            "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER");
    private static final Pattern NON_WHITESPACE = Pattern.compile("\\S", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String ACTIVE_CORRECT_OPTIONS =
            "question_option grading_option WHERE grading_option.question_id = question.id "
            + "AND grading_option.deleted = 0 AND grading_option.is_correct = 1";
    private static final String CORRECT_OPTION_COUNT = "(SELECT COUNT(*) FROM " + ACTIVE_CORRECT_OPTIONS + ")";
    private static final String USABLE_ANSWER_EXISTS = "("
            + "(question.question_type = 'SINGLE_CHOICE' AND " + CORRECT_OPTION_COUNT + " = 1 AND EXISTS "
            + "(SELECT 1 FROM " + ACTIVE_CORRECT_OPTIONS + " AND grading_option.option_label REGEXP '[^[:space:]]')) "
            + "OR (question.question_type = 'MULTIPLE_CHOICE' AND EXISTS "
            + "(SELECT 1 FROM " + ACTIVE_CORRECT_OPTIONS + " AND grading_option.option_label REGEXP '[^[:space:]]')) "
            + "OR (question.question_type = 'TRUE_FALSE' AND " + CORRECT_OPTION_COUNT + " = 1 AND EXISTS "
            + "(SELECT 1 FROM " + ACTIVE_CORRECT_OPTIONS + " AND (UPPER(TRIM(grading_option.content)) "
            + "IN ('TRUE', 'FALSE', 'A', 'B') OR TRIM(grading_option.content) IN ('正确', '错误', '对', '错')))) "
            + "OR (question.question_type IN ('FILL_BLANK', 'SHORT_ANSWER') AND EXISTS "
            + "(SELECT 1 FROM " + ACTIVE_CORRECT_OPTIONS + " AND grading_option.content REGEXP '[^[:space:]]'))"
            + ")";

    private AutomaticGradingPolicy() { }

    public static void restrictCandidates(LambdaQueryWrapper<Question> wrapper) {
        wrapper.in(Question::getQuestionType, SUPPORTED_TYPES).apply(USABLE_ANSWER_EXISTS);
    }

    public static void restrictReviewCandidates(LambdaQueryWrapper<QuestionReviewSchedule> wrapper) {
        wrapper.inSql(QuestionReviewSchedule::getQuestionId, eligibleQuestionIdsSql());
    }

    public static String eligibleQuestionIdsSql() {
        String supportedTypes = "'" + String.join("','", SUPPORTED_TYPES) + "'";
        return "SELECT question.id FROM question WHERE question.deleted = 0 AND question.question_type IN ("
                + supportedTypes + ") AND " + USABLE_ANSWER_EXISTS;
    }

    public static void requireBasis(String questionType, List<QuestionOption> correctOptions) {
        if (questionType == null || !SUPPORTED_TYPES.contains(questionType)
                || !hasUsableStructure(questionType, correctOptions)) {
            throw new BusinessException("题目尚未配置可用判分依据，暂时无法提交练习");
        }
    }

    /**
     * 校验已配置的管理端答案结构；没有正确项仍可作为未配置草稿保存。
     */
    public static void validateConfiguredStructure(
            String questionType, List<String> correctLabels, List<String> correctContents) {
        if (correctLabels.isEmpty()) {
            return;
        }
        if ("SINGLE_CHOICE".equals(questionType)
                && (correctLabels.size() != 1 || !hasText(correctLabels.getFirst()))) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "单选题必须且只能有 1 个正确答案");
        }
        if ("TRUE_FALSE".equals(questionType)) {
            if (correctContents.size() != 1) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "判断题必须且只能有 1 个正确答案");
            }
            if (normalizeTrueFalseAnswer(correctContents.getFirst()) == null) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "判断题答案只能是正确/错误");
            }
        }
    }

    /** Returns the canonical stored value for the aliases accepted by question submission. */
    public static String normalizeTrueFalseAnswer(String answer) {
        if (!hasText(answer)) {
            return null;
        }
        String normalized = answer.trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("TRUE".equals(upper) || "正确".equals(normalized) || "对".equals(normalized) || "A".equals(upper)) {
            return "TRUE";
        }
        if ("FALSE".equals(upper) || "错误".equals(normalized) || "错".equals(normalized) || "B".equals(upper)) {
            return "FALSE";
        }
        return null;
    }

    private static boolean hasUsableStructure(String questionType, List<QuestionOption> correctOptions) {
        List<QuestionOption> markedCorrect = correctOptions.stream()
                .filter(option -> Integer.valueOf(1).equals(option.getIsCorrect()))
                .toList();
        return switch (questionType) {
            case "SINGLE_CHOICE" -> markedCorrect.size() == 1
                    && hasText(markedCorrect.getFirst().getOptionLabel());
            case "MULTIPLE_CHOICE" -> markedCorrect.stream()
                    .anyMatch(option -> hasText(option.getOptionLabel()));
            case "TRUE_FALSE" -> markedCorrect.size() == 1
                    && normalizeTrueFalseAnswer(markedCorrect.getFirst().getContent()) != null;
            case "FILL_BLANK", "SHORT_ANSWER" -> markedCorrect.stream()
                    .anyMatch(option -> hasText(option.getContent()));
            default -> false;
        };
    }

    private static boolean hasText(String value) {
        return value != null && NON_WHITESPACE.matcher(value).find();
    }
}
