package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.QuestionSubmissionRequest;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.QuestionSubmission;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.QuestionSubmissionMapper;
import com.learnplatform.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private UserMapper userMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;

    private QuestionSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new QuestionSubmissionService(submissionMapper, questionMapper, questionOptionMapper,
                questionKnowledgePointMapper, courseMapper, userMapper, knowledgePointMapper, new ObjectMapper());
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

    @Test
    void importFillBlankSubmissionCreatesAnswerOptionForPracticeJudging() {
        QuestionSubmission submission = approvedSubmission("FILL_BLANK");
        submission.setCorrectAnswer("CPU|内存");
        when(submissionMapper.selectById(10L)).thenReturn(submission);
        doAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(99L);
            return 1;
        }).when(questionMapper).insert(any(Question.class));

        QuestionSubmissionVO vo = service.importSubmission(10L, 1L);

        assertEquals(99L, vo.getImportedQuestionId());

        ArgumentCaptor<QuestionOption> optionCaptor = ArgumentCaptor.forClass(QuestionOption.class);
        verify(questionOptionMapper).insert(optionCaptor.capture());
        QuestionOption option = optionCaptor.getValue();
        assertEquals(99L, option.getQuestionId());
        assertEquals("ANSWER", option.getOptionLabel());
        assertEquals("CPU|内存", option.getContent());
        assertEquals(1, option.getIsCorrect());
        assertEquals(0, option.getSortOrder());

        ArgumentCaptor<QuestionSubmission> submissionCaptor = ArgumentCaptor.forClass(QuestionSubmission.class);
        verify(submissionMapper).updateById(submissionCaptor.capture());
        QuestionSubmission updated = submissionCaptor.getValue();
        assertEquals(3, updated.getStatus());
        assertEquals(99L, updated.getImportedQuestionId());
    }

    @Test
    void importTrueFalseSubmissionCreatesOneCorrectFormalOption() {
        QuestionSubmission submission = approvedSubmission("TRUE_FALSE");
        submission.setOptionsJson("""
                [
                  {"content":"正确","label":"A","isCorrect":true},
                  {"content":"错误","label":"B","isCorrect":false}
                ]
                """);
        when(submissionMapper.selectById(10L)).thenReturn(submission);
        doAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(99L);
            return 1;
        }).when(questionMapper).insert(any(Question.class));

        service.importSubmission(10L, 1L);

        ArgumentCaptor<QuestionOption> optionCaptor = ArgumentCaptor.forClass(QuestionOption.class);
        verify(questionOptionMapper, org.mockito.Mockito.times(2)).insert(optionCaptor.capture());
        assertEquals(1, optionCaptor.getAllValues().get(0).getIsCorrect());
        assertEquals(0, optionCaptor.getAllValues().get(1).getIsCorrect());
        assertNotNull(optionCaptor.getAllValues().get(0).getOptionLabel());
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

    private QuestionSubmission approvedSubmission(String questionType) {
        QuestionSubmission submission = new QuestionSubmission();
        submission.setId(10L);
        submission.setUserId(7L);
        submission.setContent("题干");
        submission.setQuestionType(questionType);
        submission.setCourseId(1L);
        submission.setDifficulty(3);
        submission.setAnalysis("解析");
        submission.setStatus(1);
        submission.setDeleted(0);
        return submission;
    }
}
