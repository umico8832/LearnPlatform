package com.learnplatform.service;

import com.learnplatform.dto.QuestionDuplicateGroupVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private QuestionVersionService questionVersionService;

    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        questionService = new QuestionService(
                questionMapper,
                questionOptionMapper,
                questionKnowledgePointMapper,
                courseMapper,
                knowledgePointMapper,
                examQuestionMapper,
                questionVersionService);
    }

    @Test
    void findDuplicateGroups_exactContent_returnsExactGroup() {
        Course course = new Course();
        course.setId(1L);
        course.setName("Java");
        when(questionMapper.selectList(any())).thenReturn(List.of(
                question(1L, "Java 中 == 和 equals 有什么区别？", 1L, "SHORT_ANSWER"),
                question(2L, "Java中==和equals有什么区别", 1L, "SHORT_ANSWER"),
                question(3L, "什么是 Spring Bean 生命周期？", 1L, "SHORT_ANSWER")));
        when(courseMapper.selectById(eq(1L))).thenReturn(course);
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(option(10L, 1L, "A", "Object")));
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of(questionKnowledgePoint(1L, 11L)));
        when(knowledgePointMapper.selectById(11L)).thenReturn(knowledgePoint(11L, "对象比较"));

        List<QuestionDuplicateGroupVO> groups = questionService.findDuplicateGroups(1L, "SHORT_ANSWER", 92, 20);

        assertEquals(1, groups.size());
        assertEquals("EXACT", groups.get(0).getMatchType());
        assertEquals(100, groups.get(0).getSimilarityScore());
        assertEquals(List.of(1L, 2L), groups.get(0).getQuestions().stream().map(q -> q.getId()).toList());
        assertEquals("Java", groups.get(0).getQuestions().get(0).getCourseName());
        assertEquals(List.of("A"), groups.get(0).getQuestions().get(0).getOptions().stream()
                .map(option -> option.getOptionLabel()).toList());
        assertEquals(List.of(11L), groups.get(0).getQuestions().get(0).getKnowledgePointIds());
        assertEquals(List.of("对象比较"), groups.get(0).getQuestions().get(0).getKnowledgePointNames());
    }

    @Test
    void findDuplicateGroups_clampsThresholdAndLimit() {
        when(questionMapper.selectList(any())).thenReturn(List.of(
                question(1L, "exact-content-a", 1L, "SHORT_ANSWER"),
                question(2L, "exact content a", 1L, "SHORT_ANSWER"),
                question(3L, "abcdefghij", 2L, "SHORT_ANSWER"),
                question(4L, "abcdefgXiX", 2L, "SHORT_ANSWER")));
        when(courseMapper.selectById(any())).thenReturn(new Course());
        when(questionOptionMapper.selectList(any())).thenReturn(List.of());
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of());

        List<QuestionDuplicateGroupVO> lowerClamped = questionService.findDuplicateGroups(null, null, 60, 0);
        List<QuestionDuplicateGroupVO> upperClamped = questionService.findDuplicateGroups(null, null, 200, 20);

        assertEquals(1, lowerClamped.size());
        assertEquals(100, lowerClamped.get(0).getSimilarityScore());
        assertEquals(1, upperClamped.size());
        assertEquals(List.of(1L, 2L),
                upperClamped.get(0).getQuestions().stream().map(question -> question.getId()).toList());
    }

    private Question question(Long id, String content, Long courseId, String questionType) {
        Question question = new Question();
        question.setId(id);
        question.setContent(content);
        question.setCourseId(courseId);
        question.setQuestionType(questionType);
        question.setDifficulty(3);
        question.setScore(1);
        question.setStatus(1);
        return question;
    }

    private QuestionOption option(Long id, Long questionId, String label, String content) {
        QuestionOption option = new QuestionOption();
        option.setId(id);
        option.setQuestionId(questionId);
        option.setOptionLabel(label);
        option.setContent(content);
        option.setSortOrder(1);
        return option;
    }

    private QuestionKnowledgePoint questionKnowledgePoint(Long questionId, Long knowledgePointId) {
        QuestionKnowledgePoint relation = new QuestionKnowledgePoint();
        relation.setQuestionId(questionId);
        relation.setKnowledgePointId(knowledgePointId);
        return relation;
    }

    private KnowledgePoint knowledgePoint(Long id, String name) {
        KnowledgePoint knowledgePoint = new KnowledgePoint();
        knowledgePoint.setId(id);
        knowledgePoint.setName(name);
        return knowledgePoint;
    }
}
