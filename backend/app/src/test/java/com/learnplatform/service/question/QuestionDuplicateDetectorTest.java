package com.learnplatform.service.question;

import com.learnplatform.entity.Question;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionDuplicateDetectorTest {

    @Test
    void detectNormalizesCaseWhitespaceAndPunctuationAsExactContent() {
        List<QuestionDuplicateDetector.DuplicateGroup> groups = QuestionDuplicateDetector.detect(List.of(
                question(1L, "Java 中 == 和 equals 有什么区别？", 1L, "SHORT_ANSWER"),
                question(2L, "java中==和EQUALS有什么区别", 1L, "SHORT_ANSWER")), 92);

        assertEquals(1, groups.size());
        assertEquals("EXACT", groups.get(0).matchType());
        assertEquals(100, groups.get(0).similarityScore());
        assertEquals(List.of(1L, 2L), groups.get(0).questions().stream().map(Question::getId).toList());
    }

    @Test
    void detectGroupsSimilarContentOnlyWithinSameCourseAndTypeBucket() {
        List<QuestionDuplicateDetector.DuplicateGroup> groups = QuestionDuplicateDetector.detect(List.of(
                question(1L, "请说明 HTTP 与 HTTPS 的主要区别", 1L, "SHORT_ANSWER"),
                question(2L, "请说明HTTP和HTTPS的主要区别", 1L, "SHORT_ANSWER"),
                question(3L, "请说明 HTTP 与 HTTPS 的主要区别", 2L, "SHORT_ANSWER")), 92);

        assertEquals(1, groups.size());
        assertEquals("SIMILAR", groups.get(0).matchType());
        assertTrue(groups.get(0).similarityScore() >= 92);
        assertEquals(List.of(1L, 2L), groups.get(0).questions().stream().map(Question::getId).toList());
    }

    @Test
    void detectUsesTransitiveSimilarityToBuildOneGroup() {
        List<QuestionDuplicateDetector.DuplicateGroup> groups = QuestionDuplicateDetector.detect(List.of(
                question(1L, "abcdefghij", 1L, "SHORT_ANSWER"),
                question(2L, "abcdefghiX", 1L, "SHORT_ANSWER"),
                question(3L, "abcdefgXiX", 1L, "SHORT_ANSWER")), 90);

        assertEquals(1, groups.size());
        assertEquals(List.of(1L, 2L, 3L),
                groups.get(0).questions().stream().map(Question::getId).toList());
        assertEquals(90, groups.get(0).similarityScore());
    }

    @Test
    void detectSortsBySimilarityThenGroupSize() {
        List<QuestionDuplicateDetector.DuplicateGroup> groups = QuestionDuplicateDetector.detect(List.of(
                question(1L, "exact-content-a", 1L, "SHORT_ANSWER"),
                question(2L, "exact content a", 1L, "SHORT_ANSWER"),
                question(3L, "larger exact group", 2L, "SHORT_ANSWER"),
                question(4L, "larger-exact-group", 2L, "SHORT_ANSWER"),
                question(5L, "LARGER EXACT GROUP", 2L, "SHORT_ANSWER"),
                question(6L, "abcdefghij", 3L, "SHORT_ANSWER"),
                question(7L, "abcdefghiX", 3L, "SHORT_ANSWER")), 90);

        assertEquals(List.of(List.of(3L, 4L, 5L), List.of(1L, 2L), List.of(6L, 7L)),
                groups.stream()
                        .map(group -> group.questions().stream().map(Question::getId).toList())
                        .toList());
        assertEquals(List.of(100, 100, 90),
                groups.stream().map(QuestionDuplicateDetector.DuplicateGroup::similarityScore).toList());
    }

    @Test
    void detectIgnoresShortContentAndUnrelatedQuestions() {
        List<QuestionDuplicateDetector.DuplicateGroup> groups = QuestionDuplicateDetector.detect(List.of(
                question(1L, "太短", 1L, "SHORT_ANSWER"),
                question(2L, "什么是 JVM 类加载机制？", 1L, "SHORT_ANSWER"),
                question(3L, "数据库索引为什么能提升查询性能？", 1L, "SHORT_ANSWER")), 92);

        assertTrue(groups.isEmpty());
    }

    private Question question(Long id, String content, Long courseId, String questionType) {
        Question question = new Question();
        question.setId(id);
        question.setContent(content);
        question.setCourseId(courseId);
        question.setQuestionType(questionType);
        return question;
    }
}
