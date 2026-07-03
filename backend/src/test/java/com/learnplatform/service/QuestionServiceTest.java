package com.learnplatform.service;

import com.learnplatform.dto.QuestionDuplicateGroupVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        when(questionOptionMapper.selectList(any())).thenReturn(List.of());
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of());

        List<QuestionDuplicateGroupVO> groups = questionService.findDuplicateGroups(1L, "SHORT_ANSWER", 92, 20);

        assertEquals(1, groups.size());
        assertEquals("EXACT", groups.get(0).getMatchType());
        assertEquals(100, groups.get(0).getSimilarityScore());
        assertEquals(List.of(1L, 2L), groups.get(0).getQuestions().stream().map(q -> q.getId()).toList());
        assertEquals("Java", groups.get(0).getQuestions().get(0).getCourseName());
    }

    @Test
    void findDuplicateGroups_similarContent_returnsSimilarGroupWithinSameBucket() {
        when(questionMapper.selectList(any())).thenReturn(List.of(
                question(1L, "请说明 HTTP 与 HTTPS 的主要区别", 1L, "SHORT_ANSWER"),
                question(2L, "请说明HTTP和HTTPS的主要区别", 1L, "SHORT_ANSWER"),
                question(3L, "请说明 HTTP 与 HTTPS 的主要区别", 2L, "SHORT_ANSWER")));
        when(courseMapper.selectById(any())).thenReturn(new Course());
        when(questionOptionMapper.selectList(any())).thenReturn(List.of());
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of());

        List<QuestionDuplicateGroupVO> groups = questionService.findDuplicateGroups(null, null, 92, 20);

        assertEquals(1, groups.size());
        assertEquals("SIMILAR", groups.get(0).getMatchType());
        assertTrue(groups.get(0).getSimilarityScore() >= 92);
        assertEquals(List.of(1L, 2L), groups.get(0).getQuestions().stream().map(q -> q.getId()).toList());
    }

    @Test
    void findDuplicateGroups_noMatch_returnsEmptyList() {
        when(questionMapper.selectList(any())).thenReturn(List.of(
                question(1L, "什么是 JVM 类加载机制？", 1L, "SHORT_ANSWER"),
                question(2L, "数据库索引为什么能提升查询性能？", 1L, "SHORT_ANSWER")));

        List<QuestionDuplicateGroupVO> groups = questionService.findDuplicateGroups(null, null, 92, 20);

        assertTrue(groups.isEmpty());
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
}
