package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.ExamLearningAnswerResultVO;
import com.learnplatform.dto.ExamLearningSessionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.ExamLearningAiInteraction;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamLearningAiInteractionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamLearningAiServiceTest {

    @Mock private ExamPaperLearningService learningService;
    @Mock private ExamLearningAiInteractionMapper interactionMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private AiService aiService;
    @Mock private CourseLearningEventService courseLearningEventService;
    private ExamLearningAiService service;

    @BeforeEach
    void setUp() {
        service = new ExamLearningAiService(
                learningService, interactionMapper, courseMapper, aiService, courseLearningEventService);
    }

    @Test
    void bindsSuccessfulAssistanceToSessionQuestionCourseAndLatestAnswer() {
        when(learningService.getSession(30L, 7L)).thenReturn(answeredSession());
        Course course = new Course();
        course.setName("408 数据结构");
        when(courseMapper.selectById(20L)).thenReturn(course);
        doAnswer(invocation -> {
            ExamLearningAiInteraction interaction = invocation.getArgument(0);
            interaction.setId(90L);
            return 1;
        }).when(interactionMapper).insert(any());
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(4);
            consumer.accept("辅导内容");
            return null;
        }).when(aiService).generatePaperLearningAssistanceStream(
                eq(10L), eq("EXPLANATION"), any(), eq(7L), any());

        StringBuilder content = new StringBuilder();
        service.streamAssistance(30L, 10L, "explanation", 7L, content::append);

        assertEquals("辅导内容", content.toString());
        ArgumentCaptor<ExamLearningAiInteraction> interactionCaptor =
                ArgumentCaptor.forClass(ExamLearningAiInteraction.class);
        verify(interactionMapper).insert(interactionCaptor.capture());
        ExamLearningAiInteraction interaction = interactionCaptor.getValue();
        assertEquals(7L, interaction.getUserId());
        assertEquals(20L, interaction.getCourseId());
        assertEquals(2L, interaction.getExamPaperId());
        assertEquals(30L, interaction.getLearningSessionId());
        assertEquals(10L, interaction.getQuestionId());
        assertEquals(81L, interaction.getAnswerId());
        assertEquals(1, interaction.getAnswerAttemptNo());
        assertEquals(0, interaction.getAnswerCorrect());
        assertEquals("EXPLANATION", interaction.getInteractionType());
        assertEquals(1, interaction.getStatus());
        assertTrue(interaction.getCompleteTime() != null);

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).generatePaperLearningAssistanceStream(
                eq(10L), eq("EXPLANATION"), contextCaptor.capture(), eq(7L), any());
        String context = contextCaptor.getValue();
        assertTrue(context.contains("408 数据结构"));
        assertTrue(context.contains("2026 年 408 真题"));
        assertTrue(context.contains("第1题"));
        assertTrue(context.contains("用户最近答案：B"));
        assertTrue(context.contains("第 1 次尝试，结果：错误"));

        verify(courseLearningEventService).recordPaperLearningAiAssistance(
                7L, 20L, 10L, 90L, 30L, 2L, "EXPLANATION", 81L, interaction.getCompleteTime());
    }

    @Test
    void rejectsAssistanceBeforeFirstAnswerWithoutCreatingTrace() {
        ExamLearningSessionVO session = answeredSession();
        session.getQuestions().get(0).setLatestAnswer(null);
        when(learningService.getSession(30L, 7L)).thenReturn(session);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.streamAssistance(30L, 10L, "EXPLANATION", 7L, ignored -> { }));

        assertEquals("请先完成本题首次作答再使用 AI 辅导", exception.getMessage());
        verify(interactionMapper, never()).insert(any());
        verify(aiService, never()).generatePaperLearningAssistanceStream(any(), any(), any(), any(), any());
    }

    @Test
    void recordsFailedAssistanceWithoutCreatingCourseEvent() {
        when(learningService.getSession(30L, 7L)).thenReturn(answeredSession());
        doAnswer(invocation -> {
            ExamLearningAiInteraction interaction = invocation.getArgument(0);
            interaction.setId(91L);
            return 1;
        }).when(interactionMapper).insert(any());
        doThrow(new IllegalStateException("provider unavailable"))
                .when(aiService).generatePaperLearningAssistanceStream(
                        eq(10L), eq("VARIANT"), any(), eq(7L), any());

        assertThrows(IllegalStateException.class,
                () -> service.streamAssistance(30L, 10L, "variant", 7L, ignored -> { }));

        ArgumentCaptor<ExamLearningAiInteraction> updateCaptor =
                ArgumentCaptor.forClass(ExamLearningAiInteraction.class);
        verify(interactionMapper).updateById(updateCaptor.capture());
        assertEquals(2, updateCaptor.getValue().getStatus());
        assertEquals("provider unavailable", updateCaptor.getValue().getErrorMessage());
        verify(courseLearningEventService, never()).recordPaperLearningAiAssistance(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private ExamLearningSessionVO answeredSession() {
        ExamLearningAnswerResultVO answer = new ExamLearningAnswerResultVO();
        answer.setAnswerId(81L);
        answer.setAttemptNo(1);
        answer.setUserAnswer("B");
        answer.setCorrect(false);

        ExamLearningSessionVO.QuestionItem question = new ExamLearningSessionVO.QuestionItem();
        question.setQuestionId(10L);
        question.setSectionTitle("一、单项选择题（数据结构）");
        question.setDisplayNumber("第1题");
        question.setLatestAnswer(answer);

        ExamLearningSessionVO session = new ExamLearningSessionVO();
        session.setId(30L);
        session.setExamPaperId(2L);
        session.setPaperTitle("2026 年 408 真题");
        session.setCourseId(20L);
        session.setStatus(0);
        session.setQuestions(List.of(question));
        return session;
    }
}
