package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.QuestionSubmissionRequest;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.QuestionSubmission;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionSubmissionMapper;
import com.learnplatform.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionSubmissionServiceTest {

    @Mock private QuestionSubmissionMapper submissionMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private UserMapper userMapper;

    private QuestionSubmissionService service;

    @BeforeEach
    void setUp() {
        QuestionSubmissionOptionService optionService = new QuestionSubmissionOptionService(new ObjectMapper());
        QuestionSubmissionViewService viewService = new QuestionSubmissionViewService(userMapper, courseMapper);
        service = new QuestionSubmissionService(submissionMapper, courseMapper, optionService, viewService);
    }

    @Test
    void submitSingleChoiceRejectsMultipleCorrectAnswers() {
        when(courseMapper.selectById(1L)).thenReturn(course());

        QuestionSubmissionRequest request = baseRequest("SINGLE_CHOICE");
        request.setOptionsJson("""
                [
                  {"content":"选项 A","label":"A","isCorrect":true},
                  {"content":"选项 B","label":"B","isCorrect":true}
                ]
                """);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submitQuestion(request, 7L));

        assertEquals("单选题必须且只能有 1 个正确答案", exception.getMessage());
        verify(submissionMapper, never()).insert(any());
    }

    @Test
    void submitTrueFalseNormalizesAnswerAndOptions() {
        when(courseMapper.selectById(1L)).thenReturn(course());
        doAnswer(invocation -> {
            QuestionSubmission submission = invocation.getArgument(0);
            submission.setId(10L);
            return 1;
        }).when(submissionMapper).insert(any(QuestionSubmission.class));

        QuestionSubmissionRequest request = baseRequest("TRUE_FALSE");
        request.setCorrectAnswer("错误");

        QuestionSubmissionVO vo = service.submitQuestion(request, 7L);

        assertEquals(10L, vo.getId());
        ArgumentCaptor<QuestionSubmission> captor = ArgumentCaptor.forClass(QuestionSubmission.class);
        verify(submissionMapper).insert(captor.capture());
        QuestionSubmission saved = captor.getValue();
        assertEquals("FALSE", saved.getCorrectAnswer());
        assertTrue(saved.getOptionsJson().contains("\"content\":\"正确\""));
        assertTrue(saved.getOptionsJson().contains("\"content\":\"错误\""));
        assertTrue(saved.getOptionsJson().contains("\"isCorrect\":true"));
    }

    private QuestionSubmissionRequest baseRequest(String questionType) {
        QuestionSubmissionRequest request = new QuestionSubmissionRequest();
        request.setContent("题干");
        request.setQuestionType(questionType);
        request.setCourseId(1L);
        request.setDifficulty(3);
        return request;
    }

    private Course course() {
        Course course = new Course();
        course.setId(1L);
        course.setName("课程");
        return course;
    }

}
