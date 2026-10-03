package com.learnplatform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Mock private CacheEvictService cacheEvictService;

    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "QuestionServiceTest"),
                Question.class);
        QuestionViewService viewService = new QuestionViewService(
                questionOptionMapper, questionKnowledgePointMapper, courseMapper, knowledgePointMapper);
        QuestionMutationService mutationService = new QuestionMutationService(
                questionMapper, questionOptionMapper, questionKnowledgePointMapper, courseMapper,
                knowledgePointMapper, examQuestionMapper, questionVersionService, cacheEvictService);
        questionService = new QuestionService(questionMapper, knowledgePointMapper, viewService, mutationService);
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

    @Test
    void getEnabledQuestionPage_filtersByQuestionAndKnowledgePoint_withoutLeakingAnswers() {
        Question question = question(21L, "题目", 1L, "SINGLE_CHOICE");
        question.setVisibility("PUBLIC");
        question.setAnalysis("仅供判分的解析");
        Page<Question> result = new Page<>(1, 10, 1);
        result.setRecords(List.of(question));
        when(knowledgePointMapper.selectQuestionIdsByKnowledgePointId(31L)).thenReturn(List.of(21L, 22L));
        when(questionMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(result);
        when(courseMapper.selectById(1L)).thenReturn(new Course());
        QuestionOption correctOption = option(1L, 21L, "A", "选项");
        correctOption.setIsCorrect(1);
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(correctOption));
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of(questionKnowledgePoint(21L, 31L)));

        Page<com.learnplatform.dto.QuestionVO> page = questionService.getEnabledQuestionPage(
                1, 10, null, null, null, 21L, 31L);

        ArgumentCaptor<LambdaQueryWrapper<Question>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(questionMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertEquals(List.of(21L), page.getRecords().stream().map(item -> item.getId()).toList());
        assertNull(page.getRecords().getFirst().getAnalysis());
        assertNull(page.getRecords().getFirst().getOptions().getFirst().getIsCorrect());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("status"));
        assertTrue(sqlSegment.contains("visibility"));
        List<Object> filterValues = wrapperCaptor.getValue().getParamNameValuePairs().values().stream().toList();
        assertTrue(filterValues.stream().anyMatch(value -> value instanceof Number number && number.intValue() == 1));
        assertTrue(filterValues.contains("PUBLIC"));
        assertTrue(filterValues.contains(21L));
        assertTrue(filterValues.contains(22L));
        verify(knowledgePointMapper).selectQuestionIdsByKnowledgePointId(31L);
    }

    @Test
    void getEnabledQuestionPage_returnsEmptyPageWhenKnowledgePointHasNoQuestions() {
        when(knowledgePointMapper.selectQuestionIdsByKnowledgePointId(31L)).thenReturn(List.of());

        Page<com.learnplatform.dto.QuestionVO> page = questionService.getEnabledQuestionPage(
                2, 5, null, null, null, null, 31L);

        assertEquals(0, page.getTotal());
        assertEquals(List.of(), page.getRecords());
        verify(questionMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
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
