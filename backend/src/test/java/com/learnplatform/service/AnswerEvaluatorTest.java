package com.learnplatform.service;

import com.learnplatform.entity.QuestionOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnswerEvaluatorTest {

    private final AnswerEvaluator evaluator = new AnswerEvaluator();

    // ======================== buildCorrectAnswer ========================

    @Test
    void buildsSortedMultipleChoiceAnswer() {
        assertEquals("A,C", evaluator.buildCorrectAnswer(
                List.of(option("C", "C"), option("A", "A")), "MULTIPLE_CHOICE"));
    }

    @Test
    void buildsTrueFalseAnswerFromOptionContent() {
        assertEquals("TRUE", evaluator.buildCorrectAnswer(List.of(option("A", "正确")), "TRUE_FALSE"));
        assertEquals("FALSE", evaluator.buildCorrectAnswer(List.of(option("B", "错误")), "TRUE_FALSE"));
    }

    @Test
    void buildsFillBlankAndShortAnswerFromOptionContent() {
        assertEquals("CPU|内存", evaluator.buildCorrectAnswer(
                List.of(option("ANSWER", "CPU|内存")), "FILL_BLANK"));
        assertEquals("TCP|三次握手", evaluator.buildCorrectAnswer(
                List.of(option("ANSWER", "TCP|三次握手")), "SHORT_ANSWER"));
    }

    // ======================== SINGLE_CHOICE / TRUE_FALSE ========================

    @Test
    void singleChoiceIgnoresCase() {
        assertTrue(evaluator.isCorrect("SINGLE_CHOICE", "a", "A"));
        assertTrue(evaluator.isCorrect("SINGLE_CHOICE", " A ", "A"));
        assertFalse(evaluator.isCorrect("SINGLE_CHOICE", "B", "A"));
    }

    @Test
    void trueFalseIgnoresCase() {
        assertTrue(evaluator.isCorrect("TRUE_FALSE", "true", "TRUE"));
        assertTrue(evaluator.isCorrect("TRUE_FALSE", " FALSE ", "FALSE"));
        assertFalse(evaluator.isCorrect("TRUE_FALSE", "TRUE", "FALSE"));
    }

    // ======================== MULTIPLE_CHOICE ========================

    @Test
    void multipleChoiceIgnoresCaseAndOrder() {
        assertTrue(evaluator.isCorrect("MULTIPLE_CHOICE", " c, A ", "A,C"));
        assertTrue(evaluator.isCorrect("MULTIPLE_CHOICE", "A,C", "C,A"));
        assertFalse(evaluator.isCorrect("MULTIPLE_CHOICE", "A", "A,C"));
        assertFalse(evaluator.isCorrect("MULTIPLE_CHOICE", "A,B,C", "A,C"));
    }

    // ======================== FILL_BLANK ========================

    @Test
    void fillBlankSingleBlankIgnoresCase() {
        assertTrue(evaluator.isCorrect("FILL_BLANK", " Java ", "java"));
        assertTrue(evaluator.isCorrect("FILL_BLANK", "JAVA", "java"));
        assertFalse(evaluator.isCorrect("FILL_BLANK", "Python", "java"));
    }

    @Test
    void fillBlankMultipleBlanksComparedInOrder() {
        assertTrue(evaluator.isCorrect("FILL_BLANK", "CPU|内存|硬盘", "CPU|内存|硬盘"));
        assertTrue(evaluator.isCorrect("FILL_BLANK", "cpu|内存|硬盘", "CPU|内存|硬盘"));
        assertFalse(evaluator.isCorrect("FILL_BLANK", "硬盘|CPU|内存", "CPU|内存|硬盘"));
    }

    @Test
    void fillBlankBlanksCountMismatchIsWrong() {
        assertFalse(evaluator.isCorrect("FILL_BLANK", "CPU|内存", "CPU|内存|硬盘"));
        assertFalse(evaluator.isCorrect("FILL_BLANK", "CPU|内存|硬盘", "CPU|内存"));
    }

    @Test
    void fillBlankAcceptsMultipleAnswersPerBlank() {
        // 单个空可接受多个答案，用逗号分隔
        assertTrue(evaluator.isCorrect("FILL_BLANK", "CPU", "CPU,中央处理器,处理器"));
        assertTrue(evaluator.isCorrect("FILL_BLANK", "中央处理器", "CPU,中央处理器,处理器"));
        assertTrue(evaluator.isCorrect("FILL_BLANK", "处理器", "CPU,中央处理器,处理器"));
        assertFalse(evaluator.isCorrect("FILL_BLANK", "显卡", "CPU,中央处理器,处理器"));
    }

    @Test
    void fillBlankMultipleBlankWithMultipleAnswers() {
        // 多空 + 每空多个可接受答案
        assertTrue(evaluator.isCorrect("FILL_BLANK", "CPU|内存", "CPU,中央处理器|内存,RAM"));
        assertTrue(evaluator.isCorrect("FILL_BLANK", "中央处理器|RAM", "CPU,中央处理器|内存,RAM"));
        assertFalse(evaluator.isCorrect("FILL_BLANK", "GPU|RAM", "CPU,中央处理器|内存,RAM"));
    }

    @Test
    void fillBlankBlankContentWithSpaces() {
        // 单个空内容含前后空格
        assertTrue(evaluator.isCorrect("FILL_BLANK", "  CPU  | 内存 ", "CPU|内存"));
    }

    // ======================== SHORT_ANSWER ========================

    @Test
    void shortAnswerMatchesAnyKeyword() {
        // 包含任意一个关键词即正确
        assertTrue(evaluator.isCorrect("SHORT_ANSWER",
                "TCP是一种可靠的传输层协议", "TCP|三次握手|可靠传输"));
        assertTrue(evaluator.isCorrect("SHORT_ANSWER",
                "通过三次握手建立连接", "TCP|三次握手|可靠传输"));
        assertTrue(evaluator.isCorrect("SHORT_ANSWER",
                "它提供可靠传输服务", "TCP|三次握手|可靠传输"));
    }

    @Test
    void shortAnswerNoKeywordMatchIsWrong() {
        assertFalse(evaluator.isCorrect("SHORT_ANSWER",
                "UDP是无连接的协议", "TCP|三次握手|可靠传输"));
    }

    @Test
    void shortAnswerIgnoresCase() {
        assertTrue(evaluator.isCorrect("SHORT_ANSWER",
                "tcp is a transport protocol", "TCP|transport"));
        assertTrue(evaluator.isCorrect("SHORT_ANSWER",
                "TCP协议", "tcp|协议"));
    }

    @Test
    void shortAnswerNormalizedWhitespace() {
        assertTrue(evaluator.isCorrect("SHORT_ANSWER",
                "TCP  是  传输层协议", "TCP|传输层"));
    }

    @Test
    void shortAnswerEmptyUserAnswerIsWrong() {
        assertFalse(evaluator.isCorrect("SHORT_ANSWER", "", "TCP|传输层"));
        assertFalse(evaluator.isCorrect("SHORT_ANSWER", "   ", "TCP|传输层"));
    }

    // ======================== null 安全 ========================

    @Test
    void nullUserAnswerIsWrong() {
        assertFalse(evaluator.isCorrect("SINGLE_CHOICE", null, "A"));
        assertFalse(evaluator.isCorrect("FILL_BLANK", null, "answer"));
        assertFalse(evaluator.isCorrect("SHORT_ANSWER", null, "keyword"));
    }

    @Test
    void nullCorrectAnswerIsWrong() {
        assertFalse(evaluator.isCorrect("SINGLE_CHOICE", "A", null));
    }

    @Test
    void unknownQuestionTypeReturnsFalse() {
        assertFalse(evaluator.isCorrect("UNKNOWN_TYPE", "answer", "answer"));
    }

    // ======================== 工具方法 ========================

    private QuestionOption option(String label, String content) {
        QuestionOption option = new QuestionOption();
        option.setOptionLabel(label);
        option.setContent(content);
        return option;
    }
}
