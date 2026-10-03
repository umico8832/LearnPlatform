package com.learnplatform.service.question;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.QuestionReviewSchedule;

import java.util.List;
import java.util.regex.Pattern;

public final class AutomaticGradingPolicy {
    private static final List<String> SUPPORTED_TYPES = List.of(
            "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER");
    private static final Pattern NON_WHITESPACE = Pattern.compile("\\S", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String USABLE_ANSWER_EXISTS =
            "SELECT 1 FROM question_option grading_option "
            + "WHERE grading_option.question_id = question.id "
            + "AND grading_option.deleted = 0 AND grading_option.is_correct = 1 "
            + "AND (CASE WHEN question.question_type IN ('TRUE_FALSE', 'FILL_BLANK', 'SHORT_ANSWER') "
            + "THEN grading_option.content ELSE grading_option.option_label END) REGEXP '[^[:space:]]'";

    private AutomaticGradingPolicy() { }

    public static void restrictCandidates(LambdaQueryWrapper<Question> wrapper) {
        wrapper.in(Question::getQuestionType, SUPPORTED_TYPES).exists(USABLE_ANSWER_EXISTS);
    }

    public static void restrictReviewCandidates(LambdaQueryWrapper<QuestionReviewSchedule> wrapper) {
        wrapper.inSql(QuestionReviewSchedule::getQuestionId, eligibleQuestionIdsSql());
    }

    public static String eligibleQuestionIdsSql() {
        String supportedTypes = "'" + String.join("','", SUPPORTED_TYPES) + "'";
        return "SELECT question.id FROM question WHERE question.deleted = 0 AND question.question_type IN ("
                + supportedTypes + ") AND EXISTS (" + USABLE_ANSWER_EXISTS + ")";
    }

    public static void requireBasis(String questionType, List<QuestionOption> correctOptions) {
        if (questionType == null || !SUPPORTED_TYPES.contains(questionType)
                || correctOptions.stream().noneMatch(option -> hasUsableAnswer(questionType, option))) {
            throw new BusinessException("题目尚未配置可用判分依据，暂时无法提交练习");
        }
    }

    private static boolean hasUsableAnswer(String questionType, QuestionOption option) {
        String value = switch (questionType) {
            case "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER" -> option.getContent();
            default -> option.getOptionLabel();
        };
        return Integer.valueOf(1).equals(option.getIsCorrect())
                && value != null && NON_WHITESPACE.matcher(value).find();
    }
}
