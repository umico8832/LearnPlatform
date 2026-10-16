package com.learnplatform.service.understanding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestionAskPolicyTest {
    @Test void matchesAnswerStatementsAndNormalizedCorrectOptionOnlyInAnswer() {
        for (String value : List.of("答案是 A", "答案为Ａ", "应选 **a**。", "选择A", "正确选项为A", "后 进，先 出")) {
            assertFalse(QuestionAskPolicy.rulesPass(value, "A", "后进先出"), value);
        }
        for (String value : List.of("你提到 A，可以说说依据。", "先检查条件", "选择Algorithm作为变量名称")) {
            assertTrue(QuestionAskPolicy.rulesPass(value, "A", "后进先出"), value);
        }
        assertFalse(QuestionAskPolicy.rulesPass("", "A", "后进先出"));
    }

    @Test void onlyOneStrictBooleanJsonFieldCanPass() {
        assertTrue(QuestionAskPolicy.judgmentPass(" {\"safe\":true} "));
        for (String value : List.of("{\"safe\":false}", "{}", "{\"safe\":\"true\"}", "true", "[]", "null",
                "```json\n{\"safe\":true}\n```", "{\"safe\":true,\"extra\":1}", "{\"safe\":true} {}",
                "{\"safe\":false,\"safe\":true}")) {
            assertFalse(QuestionAskPolicy.judgmentPass(value), value);
        }
        assertFalse(QuestionAskPolicy.judgmentPass(null));
    }
}
