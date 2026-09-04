package com.learnplatform.service;

import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Markdown 题目解析器单元测试
 */
@ExtendWith(MockitoExtension.class)
class MarkdownQuestionParserTest {

    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionOptionMapper questionOptionMapper;
    @Mock
    private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private KnowledgePointMapper knowledgePointMapper;

    private MarkdownQuestionParser parser;

    @BeforeEach
    void setUp() {
        parser = new MarkdownQuestionParser(
                questionMapper, questionOptionMapper,
                questionKnowledgePointMapper, courseMapper, knowledgePointMapper,
                new MarkdownQuestionContentParser());
    }

    @Test
    void testParseSingleChoiceQuestion() throws Exception {
        String md = """
                ## 1. 单选题

                **题干**: 以下哪个是 Java 的基本数据类型？

                **选项**:
                - A. int
                - B. String
                - C. ArrayList
                - D. HashMap

                **答案**: A

                **解析**: int 是 Java 的 8 种基本数据类型之一。

                **课程**: Java 基础
                **难度**: 2
                """;

        List<MarkdownQuestionParser.RawQuestion> questions = parser.parseMarkdown(
                new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, questions.size());
        MarkdownQuestionParser.RawQuestion q = questions.get(0);
        assertEquals("以下哪个是 Java 的基本数据类型？", q.content);
        assertEquals("A", q.answer);
        assertEquals("int 是 Java 的 8 种基本数据类型之一。", q.analysis);
        assertEquals("Java 基础", q.course);
        assertEquals(2, q.difficulty);
        assertEquals(4, q.options.size());
        assertEquals("A", q.options.get(0).label);
        assertEquals("int", q.options.get(0).content);
        assertEquals("D", q.options.get(3).label);
        assertEquals("HashMap", q.options.get(3).content);
    }

    @Test
    void testParseTrueFalseQuestion() throws Exception {
        String md = """
                ## 2. 判断题

                **题干**: Java 是一种编译型语言。

                **选项**:
                - 对
                - 错

                **答案**: 错

                **解析**: Java 既是编译型也是解释型语言。

                **课程**: Java 基础
                **难度**: 1
                """;

        List<MarkdownQuestionParser.RawQuestion> questions = parser.parseMarkdown(
                new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, questions.size());
        MarkdownQuestionParser.RawQuestion q = questions.get(0);
        assertEquals("Java 是一种编译型语言。", q.content);
        assertEquals("错", q.answer);
        assertEquals(2, q.options.size());
        assertEquals("对", q.options.get(0).content);
        assertEquals("错", q.options.get(1).content);
    }

    @Test
    void testParseMultipleChoiceQuestion() throws Exception {
        String md = """
                ## 3. 多选题

                **题干**: 以下哪些是 Java 集合框架的接口？

                **选项**:
                - A. List
                - B. Set
                - C. Array
                - D. Map

                **答案**: A,B,D

                **课程**: Java 基础
                **难度**: 3
                """;

        List<MarkdownQuestionParser.RawQuestion> questions = parser.parseMarkdown(
                new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, questions.size());
        assertEquals(4, questions.get(0).options.size());
        assertEquals("A,B,D", questions.get(0).answer);
    }

    @Test
    void testParseMultipleQuestions() throws Exception {
        String md = """
                ## 1. 单选题

                **题干**: 第一题

                **选项**:
                - A. 选项1
                - B. 选项2

                **答案**: A

                **课程**: Java 基础

                ---

                ## 2. 判断题

                **题干**: 第二题

                **选项**:
                - 对
                - 错

                **答案**: 对

                **课程**: Java 基础
                """;

        List<MarkdownQuestionParser.RawQuestion> questions = parser.parseMarkdown(
                new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, questions.size());
        assertEquals("第一题", questions.get(0).content);
        assertEquals("第二题", questions.get(1).content);
    }

    @Test
    void testNormalizeQuestionType() {
        assertEquals("SINGLE_CHOICE", parser.normalizeQuestionType("单选"));
        assertEquals("SINGLE_CHOICE", parser.normalizeQuestionType("单选题"));
        assertEquals("SINGLE_CHOICE", parser.normalizeQuestionType("SINGLE_CHOICE"));
        assertEquals("MULTIPLE_CHOICE", parser.normalizeQuestionType("多选"));
        assertEquals("MULTIPLE_CHOICE", parser.normalizeQuestionType("MULTIPLE_CHOICE"));
        assertEquals("TRUE_FALSE", parser.normalizeQuestionType("判断"));
        assertEquals("TRUE_FALSE", parser.normalizeQuestionType("TRUE_FALSE"));
        assertEquals("TRUE_FALSE", parser.normalizeQuestionType("JUDGMENT"));
        assertEquals("FILL_BLANK", parser.normalizeQuestionType("填空"));
        assertEquals("SHORT_ANSWER", parser.normalizeQuestionType("简答"));
        assertNull(parser.normalizeQuestionType("未知题型"));
        assertNull(parser.normalizeQuestionType(null));
    }

    @Test
    void testInferTrueFalseFromOptions() throws Exception {
        String md = """
                ## 题目

                **题干**: 水的沸点是 100 度

                **选项**:
                - 对
                - 错

                **答案**: 对

                **课程**: 物理
                """;

        List<MarkdownQuestionParser.RawQuestion> questions = parser.parseMarkdown(
                new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, questions.size());
        // 题型会在 importFromMarkdown 中通过 inferQuestionType 推断
    }

    @Test
    void testInferMultipleChoiceFromAnswer() throws Exception {
        String md = """
                ## 题目

                **题干**: 以下哪些是水果？

                **选项**:
                - A. 苹果
                - B. 汽车
                - C. 香蕉
                - D. 手机

                **答案**: A,C

                **课程**: 常识
                """;

        List<MarkdownQuestionParser.RawQuestion> questions = parser.parseMarkdown(
                new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, questions.size());
        assertEquals("A,C", questions.get(0).answer);
    }

    @Test
    void testParseWithFieldsOrder() throws Exception {
        String md = """
                ## 1. 单选题

                **课程**: Java 基础
                **难度**: 4
                **题干**: 哪个关键字用于创建对象？
                **标签**: 基础,面向对象
                **分值**: 3
                **答案**: B

                **选项**:
                - A. class
                - B. new
                - C. void
                - D. static
                """;

        List<MarkdownQuestionParser.RawQuestion> questions = parser.parseMarkdown(
                new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, questions.size());
        MarkdownQuestionParser.RawQuestion q = questions.get(0);
        assertEquals("哪个关键字用于创建对象？", q.content);
        assertEquals("Java 基础", q.course);
        assertEquals(4, q.difficulty);
        assertEquals(3, q.score);
        assertEquals("基础,面向对象", q.tags);
        assertEquals("B", q.answer);
        assertEquals(4, q.options.size());
    }

    @Test
    void testEmptyMarkdown() throws Exception {
        String md = "# 空文件\n\n没有题目\n";

        List<MarkdownQuestionParser.RawQuestion> questions = parser.parseMarkdown(
                new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)));

        assertEquals(0, questions.size());
    }

    @Test
    void testParseWithKnowledgePoints() throws Exception {
        String md = """
                ## 1. 单选题

                **题干**: 测试题

                **选项**:
                - A. 选项A
                - B. 选项B

                **答案**: A

                **课程**: 测试课程
                **知识点**: 知识点1, 知识点2
                """;

        List<MarkdownQuestionParser.RawQuestion> questions = parser.parseMarkdown(
                new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, questions.size());
        assertEquals("知识点1, 知识点2", questions.get(0).knowledgePoints);
    }

    @Test
    void testHeadingPatternRecognition() throws Exception {
        String md = """
                ## 单选题

                **题干**: 标题格式直接是题型名

                **选项**:
                - A. 是
                - B. 否

                **答案**: A

                **课程**: 测试

                ## 2、多选题

                **题干**: 中文顿号分隔的序号

                **选项**:
                - A. 选项1
                - B. 选项2
                - C. 选项3

                **答案**: A,B

                **课程**: 测试
                """;

        List<MarkdownQuestionParser.RawQuestion> questions = parser.parseMarkdown(
                new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, questions.size());
    }
}
