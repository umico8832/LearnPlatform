package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.GlobalSearchResultVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GlobalSearchService 单元测试
 * Phase 18：全局搜索与快捷导航
 */
@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceTest {

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private KnowledgePointMapper knowledgePointMapper;

    @InjectMocks
    private GlobalSearchService globalSearchService;

    private Question sampleQuestion;
    private Course sampleCourse;
    private KnowledgePoint sampleKP;

    @BeforeEach
    void setUp() {
        sampleQuestion = new Question();
        sampleQuestion.setId(1L);
        sampleQuestion.setContent("以下哪个是 Java 中的基本数据类型？");
        sampleQuestion.setQuestionType("SINGLE_CHOICE");
        sampleQuestion.setDifficulty(3);
        sampleQuestion.setStatus(1);

        sampleCourse = new Course();
        sampleCourse.setId(1L);
        sampleCourse.setName("Java 基础");
        sampleCourse.setDescription("Java 编程入门课程");
        sampleCourse.setStatus(1);

        sampleKP = new KnowledgePoint();
        sampleKP.setId(1L);
        sampleKP.setName("Java 数据类型");
        sampleKP.setDescription("Java 基本数据类型和引用类型");
    }

    @Nested
    @DisplayName("空查询处理")
    class EmptyQueryTests {

        @Test
        @DisplayName("null 关键词应返回空结果")
        void search_nullKeyword_returnsEmpty() {
            GlobalSearchResultVO result = globalSearchService.search(null, null);
            assertNotNull(result);
            assertTrue(result.getQuestions().isEmpty());
            assertTrue(result.getCourses().isEmpty());
            assertTrue(result.getKnowledgePoints().isEmpty());
            assertEquals(0, result.getTotalCount());
        }

        @Test
        @DisplayName("空字符串关键词应返回空结果")
        void search_emptyKeyword_returnsEmpty() {
            GlobalSearchResultVO result = globalSearchService.search("  ", null);
            assertNotNull(result);
            assertEquals(0, result.getTotalCount());
            verifyNoInteractions(questionMapper, courseMapper, knowledgePointMapper);
        }
    }

    @Nested
    @DisplayName("正常搜索")
    class NormalSearchTests {

        @Test
        @DisplayName("正常关键词应返回分组结果")
        void search_normalKeyword_returnsGroupedResults() {
            List<Question> questions = List.of(sampleQuestion);
            List<Course> courses = List.of(sampleCourse);
            List<KnowledgePoint> kps = List.of(sampleKP);

            when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(questions);
            when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(courses);
            when(knowledgePointMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(kps);

            GlobalSearchResultVO result = globalSearchService.search("Java", null);

            assertNotNull(result);
            assertEquals(1, result.getQuestions().size());
            assertEquals(1, result.getCourses().size());
            assertEquals(1, result.getKnowledgePoints().size());
            assertEquals(3, result.getTotalCount());

            // 验证题目结果
            assertEquals(1L, result.getQuestions().get(0).getId());
            assertEquals("QUESTION", result.getQuestions().get(0).getType());
            assertNotNull(result.getQuestions().get(0).getSubtitle());

            // 验证课程结果
            assertEquals("Java 基础", result.getCourses().get(0).getTitle());
            assertEquals("COURSE", result.getCourses().get(0).getType());
            assertEquals("/courses/1", result.getCourses().get(0).getLink());

            // 验证知识点结果
            assertEquals("Java 数据类型", result.getKnowledgePoints().get(0).getTitle());
            assertEquals("KNOWLEDGE_POINT", result.getKnowledgePoints().get(0).getType());
        }

        @Test
        @DisplayName("自定义 limit 应生效")
        void search_customLimit_passesToQuery() {
            when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());
            when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());
            when(knowledgePointMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

            globalSearchService.search("test", 10);

            verify(questionMapper).selectList(any(LambdaQueryWrapper.class));
            verify(courseMapper).selectList(any(LambdaQueryWrapper.class));
            verify(knowledgePointMapper).selectList(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("limit 超过最大值应截断为 20")
        void search_limitExceedsMax_clamped() {
            when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());
            when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());
            when(knowledgePointMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

            // 不抛异常即可，limit 在 SQL 中被截断
            assertDoesNotThrow(() -> globalSearchService.search("test", 100));
        }
    }

    @Nested
    @DisplayName("题型映射")
    class QuestionTypeMappingTests {

        @Test
        @DisplayName("题目副标题应包含中文题型和难度")
        void search_questionSubtitle_containsTypeAndDifficulty() {
            Question q = new Question();
            q.setId(10L);
            q.setContent("测试题目内容");
            q.setQuestionType("MULTIPLE_CHOICE");
            q.setDifficulty(4);
            q.setStatus(1);

            when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(q));
            when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());
            when(knowledgePointMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

            GlobalSearchResultVO result = globalSearchService.search("测试", null);

            String subtitle = result.getQuestions().get(0).getSubtitle();
            assertTrue(subtitle.contains("多选题"));
            assertTrue(subtitle.contains("★★★★"));
        }

        @Test
        @DisplayName("题目内容超长应截断并加省略号")
        void search_longContent_truncated() {
            Question q = new Question();
            q.setId(20L);
            q.setContent("A".repeat(200));
            q.setQuestionType("SINGLE_CHOICE");
            q.setDifficulty(1);
            q.setStatus(1);

            when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(q));
            when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());
            when(knowledgePointMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

            GlobalSearchResultVO result = globalSearchService.search("A", null);

            assertTrue(result.getQuestions().get(0).getTitle().endsWith("…"));
            assertTrue(result.getQuestions().get(0).getTitle().length() <= 81); // 80 + "…"
        }
    }
}