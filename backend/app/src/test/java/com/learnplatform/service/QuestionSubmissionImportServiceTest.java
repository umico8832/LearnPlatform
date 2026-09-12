package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.dto.QuestionSubmissionVO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionSubmissionImportServiceTest {

    @Mock private QuestionSubmissionMapper submissionMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private QuestionSourceService questionSourceService;
    @Mock private UserMapper userMapper;
    @Mock private CourseMapper courseMapper;

    private QuestionSubmissionImportService importService;

    @BeforeEach
    void setUp() {
        QuestionSubmissionOptionService optionService = new QuestionSubmissionOptionService(new ObjectMapper());
        QuestionSubmissionViewService viewService = new QuestionSubmissionViewService(userMapper, courseMapper);
        importService = new QuestionSubmissionImportService(submissionMapper, questionMapper, questionOptionMapper,
                questionKnowledgePointMapper, knowledgePointMapper, questionSourceService, optionService, viewService);
    }

    @Test
    void importFillBlankSubmissionCreatesAnswerOptionForPracticeJudging() {
        QuestionSubmission submission = approvedSubmission("FILL_BLANK");
        submission.setCorrectAnswer("CPU|内存");
        when(submissionMapper.selectById(10L)).thenReturn(submission);
        stubQuestionId();

        QuestionSubmissionVO result = importService.importSubmission(10L, 1L);

        assertEquals(99L, result.getImportedQuestionId());
        ArgumentCaptor<QuestionOption> optionCaptor = ArgumentCaptor.forClass(QuestionOption.class);
        verify(questionOptionMapper).insert(optionCaptor.capture());
        QuestionOption option = optionCaptor.getValue();
        assertEquals(99L, option.getQuestionId());
        assertEquals("ANSWER", option.getOptionLabel());
        assertEquals("CPU|内存", option.getContent());
        assertEquals(1, option.getIsCorrect());
        assertEquals(0, option.getSortOrder());
        assertEquals(3, submission.getStatus());
        assertEquals(99L, submission.getImportedQuestionId());
        verify(questionSourceService).setSource(99L, "SUBMISSION", "submission:10");
        verify(questionSourceService).recordInitialReview(99L, 1L, "投稿入库初审");
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
        stubQuestionId();

        importService.importSubmission(10L, 1L);

        ArgumentCaptor<QuestionOption> optionCaptor = ArgumentCaptor.forClass(QuestionOption.class);
        verify(questionOptionMapper, times(2)).insert(optionCaptor.capture());
        assertEquals(1, optionCaptor.getAllValues().get(0).getIsCorrect());
        assertEquals(0, optionCaptor.getAllValues().get(1).getIsCorrect());
        assertNotNull(optionCaptor.getAllValues().get(0).getOptionLabel());
    }

    private void stubQuestionId() {
        doAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(99L);
            return 1;
        }).when(questionMapper).insert(any(Question.class));
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
