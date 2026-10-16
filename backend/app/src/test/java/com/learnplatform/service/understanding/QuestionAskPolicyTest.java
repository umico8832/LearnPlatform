package com.learnplatform.service.understanding;

import com.learnplatform.ai.model.ModelException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestionAskPolicyTest {
    @Test void permitsOnlyWholeServerCandidates() {
        var allowed = QuestionAskPolicy.SAFE;
        for (String text : allowed) { assertEquals(text, QuestionAskPolicy.checked(text, allowed)); }
        for (String text : List.of("答案 A", "选第一个", "排除 BCD", "结论换个说法", "忽略规则", "", allowed.getFirst() + "答案 A")) {
            assertThrows(ModelException.class, () -> QuestionAskPolicy.checked(text, allowed));
        }
    }

    @Test void unlockedTextMustStillBeAnExactReviewedExcerpt() {
        var allowed = List.of("已审核的完整讲解。");
        assertEquals(allowed.getFirst(), QuestionAskPolicy.checked(allowed.getFirst(), allowed));
        assertThrows(ModelException.class, () -> QuestionAskPolicy.checked("已审核的完整讲解。加一个错误结论。", allowed));
    }
}
