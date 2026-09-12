package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionAssetContextServiceTest {

    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private CourseMapper courseMapper;

    private QuestionAssetContextService service;

    @BeforeEach
    void setUp() {
        service = new QuestionAssetContextService(questionMapper, questionOptionMapper,
                questionKnowledgePointMapper, knowledgePointMapper, courseMapper);
    }

    @Test
    void loadBuildsCompleteContextInStableOrder() {
        Question question = question("SINGLE_CHOICE");
        question.setContent("What is polymorphism?");
        question.setAnalysis("Polymorphism means many forms");
        question.setCourseId(100L);
        when(questionMapper.selectById(1L)).thenReturn(question);
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(
                option("A", "Many forms", 1),
                option("B", "Single form", 0)));
        QuestionKnowledgePoint relation = new QuestionKnowledgePoint();
        relation.setKnowledgePointId(200L);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of(relation));
        KnowledgePoint knowledgePoint = new KnowledgePoint();
        knowledgePoint.setName("OOP Basics");
        when(knowledgePointMapper.selectBatchIds(List.of(200L))).thenReturn(List.of(knowledgePoint));
        Course course = new Course();
        course.setName("Java Programming");
        when(courseMapper.selectById(100L)).thenReturn(course);

        String context = service.load(1L);

        assertEquals("""
                题型：单选题
                难度：3/5
                题目：What is polymorphism?
                选项：
                  A. Many forms [正确答案]
                  B. Single form
                原始解析：Polymorphism means many forms
                知识点：OOP Basics
                所属课程：Java Programming
                """, context);
    }

    @Test
    void loadKeepsQuestionTypeLabelsAndOmitsMissingOptionalSections() {
        Question question = question("SINGLE_CHOICE");
        question.setCourseId(null);
        when(questionMapper.selectById(1L)).thenReturn(question);
        when(questionOptionMapper.selectList(any())).thenReturn(List.of());
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> labels = Map.of(
                "SINGLE_CHOICE", "单选题",
                "MULTIPLE_CHOICE", "多选题",
                "TRUE_FALSE", "判断题",
                "FILL_BLANK", "填空题",
                "SHORT_ANSWER", "简答题",
                "CUSTOM", "CUSTOM");

        labels.forEach((type, label) -> {
            question.setQuestionType(type);
            assertEquals("题型：" + label + "\n难度：3/5\n题目：Test question content\n", service.load(1L));
        });
        question.setQuestionType(null);
        assertEquals("题型：未知\n难度：3/5\n题目：Test question content\n", service.load(1L));
    }

    @Test
    void loadRejectsMissingQuestion() {
        when(questionMapper.selectById(99L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.load(99L));

        assertEquals("题目不存在", exception.getMessage());
    }

    private Question question(String type) {
        Question question = new Question();
        question.setId(1L);
        question.setQuestionType(type);
        question.setDifficulty(3);
        question.setContent("Test question content");
        return question;
    }

    private QuestionOption option(String label, String content, int correct) {
        QuestionOption option = new QuestionOption();
        option.setOptionLabel(label);
        option.setContent(content);
        option.setIsCorrect(correct);
        return option;
    }
}
