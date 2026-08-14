package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.CourseStageAssessmentCreateRequest;
import com.learnplatform.dto.CourseStageAssessmentSubmitRequest;
import com.learnplatform.dto.CourseStageAssessmentSummaryVO;
import com.learnplatform.dto.CourseStageAssessmentVO;
import com.learnplatform.entity.CourseStageAssessment;
import com.learnplatform.entity.CourseStageAssessmentQuestion;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseStageAssessmentMapper;
import com.learnplatform.mapper.CourseStageAssessmentQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class CourseStageAssessmentServiceTest {
    @Mock private CourseStageAssessmentMapper assessmentMapper;
    @Mock private CourseStageAssessmentQuestionMapper assessmentQuestionMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper optionMapper;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private SpacedRepetitionService repetitionService;
    @Mock private CourseLearningEventService eventService;
    private CourseStageAssessmentService service;

    @BeforeEach
    void setUp() {
        service = new CourseStageAssessmentService(assessmentMapper, assessmentQuestionMapper,
                questionMapper, optionMapper, new AnswerEvaluator(), new ObjectMapper(),
                wrongQuestionService, repetitionService, eventService);
    }

    @Test
    void createsSnapshotWithoutLeakingAnswersAndReusesActiveAssessment() {
        when(assessmentMapper.lockUserCourse(7L, 10L)).thenReturn(91L);
        when(assessmentMapper.selectActive(7L, 10L)).thenReturn(null);
        when(assessmentMapper.selectCandidateQuestions(7L, 10L, 5)).thenReturn(List.of(question()));
        when(assessmentMapper.countPrioritySignals(7L, 10L)).thenReturn(0L);
        when(optionMapper.selectList(any())).thenReturn(options());
        doAnswer(invocation -> {
            ((CourseStageAssessment) invocation.getArgument(0)).setId(51L);
            return 1;
        }).when(assessmentMapper).insert(any(CourseStageAssessment.class));
        when(assessmentQuestionMapper.selectByAssessmentId(anyLong())).thenAnswer(invocation -> List.of(snapshot()));

        CourseStageAssessmentCreateRequest request = new CourseStageAssessmentCreateRequest();
        request.setQuestionCount(5);
        CourseStageAssessmentVO created = service.start(7L, 10L, request);

        assertEquals("COURSE_SEQUENCE_FALLBACK", created.getSelectionStrategy());
        assertNull(created.getQuestions().get(0).getCorrectAnswer());
        assertNull(created.getQuestions().get(0).getAnalysis());
        verify(assessmentMapper).insert(any(CourseStageAssessment.class));
        ArgumentCaptor<CourseStageAssessmentQuestion> snapshotCaptor =
                ArgumentCaptor.forClass(CourseStageAssessmentQuestion.class);
        verify(assessmentQuestionMapper).insert(snapshotCaptor.capture());
        assertEquals("AI_GENERATED", snapshotCaptor.getValue().getSourceTypeSnapshot());
        assertEquals("AI_GENERATED", snapshotCaptor.getValue().getSourceCategorySnapshot());
        assertEquals(20L, snapshotCaptor.getValue().getOriginQuestionIdSnapshot());
        assertEquals(1, created.getSourceComposition().getAiGeneratedCount());
    }

    @Test
    void snapshotsVerifiedOfficialPaperCategoryInsteadOfInferringItFromManualSourceType() {
        Question official = question();
        official.setSourceType("MANUAL");
        official.setOriginQuestionId(null);
        when(assessmentMapper.lockUserCourse(7L, 10L)).thenReturn(91L);
        when(assessmentMapper.selectActive(7L, 10L)).thenReturn(null);
        when(assessmentMapper.selectCandidateQuestions(7L, 10L, 1)).thenReturn(List.of(official));
        when(assessmentMapper.countPrioritySignals(7L, 10L)).thenReturn(0L);
        when(assessmentMapper.countVerifiedOfficialPaperReferences(21L)).thenReturn(1L);
        when(optionMapper.selectList(any())).thenReturn(options());
        doAnswer(invocation -> {
            ((CourseStageAssessment) invocation.getArgument(0)).setId(51L);
            return 1;
        }).when(assessmentMapper).insert(any(CourseStageAssessment.class));
        when(assessmentQuestionMapper.selectByAssessmentId(51L)).thenAnswer(invocation -> {
            CourseStageAssessmentQuestion item = snapshot();
            item.setSourceCategorySnapshot("OFFICIAL_EXAM");
            return List.of(item);
        });
        CourseStageAssessmentCreateRequest request = new CourseStageAssessmentCreateRequest();
        request.setQuestionCount(1);

        CourseStageAssessmentVO created = service.start(7L, 10L, request);

        ArgumentCaptor<CourseStageAssessmentQuestion> captor =
                ArgumentCaptor.forClass(CourseStageAssessmentQuestion.class);
        verify(assessmentQuestionMapper).insert(captor.capture());
        assertEquals("OFFICIAL_EXAM", captor.getValue().getSourceCategorySnapshot());
        assertEquals(1, created.getSourceComposition().getOfficialExamCount());
    }

    @Test
    void classifiesPrivateAndManualQuestionsWithoutChangingTheirRawSource() {
        Question privateQuestion = question();
        privateQuestion.setVisibility("PRIVATE");
        privateQuestion.setSourceType("USER_PRIVATE_IMPORT");
        assertEquals("USER_PRIVATE", service.sourceCategory(privateQuestion));

        Question manualQuestion = question();
        manualQuestion.setVisibility("PUBLIC");
        manualQuestion.setSourceType("MARKDOWN_IMPORT");
        when(assessmentMapper.countVerifiedOfficialPaperReferences(21L)).thenReturn(0L);
        assertEquals("MANUAL", service.sourceCategory(manualQuestion));
    }

    @Test
    void rejectsPartialSubmission() {
        CourseStageAssessment assessment = new CourseStageAssessment();
        assessment.setId(51L);
        assessment.setUserId(7L);
        assessment.setCourseId(10L);
        assessment.setStatus("IN_PROGRESS");
        when(assessmentMapper.selectOwnedForUpdate(51L, 7L)).thenReturn(assessment);
        when(assessmentQuestionMapper.selectByAssessmentId(51L)).thenReturn(List.of(snapshot()));
        CourseStageAssessmentSubmitRequest request = new CourseStageAssessmentSubmitRequest();
        request.setAnswers(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submit(51L, 7L, request));

        assertEquals("必须提交完整测评答案", exception.getMessage());
    }

    @Test
    void completesServerGradingAndWritesUnifiedLearningFacts() {
        CourseStageAssessment assessment = new CourseStageAssessment();
        assessment.setId(51L);
        assessment.setUserId(7L);
        assessment.setCourseId(10L);
        assessment.setStatus("IN_PROGRESS");
        assessment.setQuestionCount(1);
        CourseStageAssessmentQuestion item = snapshot();
        when(assessmentMapper.selectOwnedForUpdate(51L, 7L)).thenReturn(assessment);
        when(assessmentQuestionMapper.selectByAssessmentId(51L)).thenReturn(List.of(item));
        when(questionMapper.selectById(21L)).thenReturn(question());
        CourseStageAssessmentSubmitRequest.Answer answer = new CourseStageAssessmentSubmitRequest.Answer();
        answer.setAssessmentQuestionId(61L);
        answer.setUserAnswer("A");
        CourseStageAssessmentSubmitRequest request = new CourseStageAssessmentSubmitRequest();
        request.setAnswers(List.of(answer));

        CourseStageAssessmentVO completed = service.submit(51L, 7L, request);

        assertEquals("COMPLETED", completed.getStatus());
        assertEquals(1, completed.getCorrectCount());
        assertEquals("A", completed.getQuestions().get(0).getCorrectAnswer());
        verify(wrongQuestionService).removeOnCorrect(7L, 21L);
        verify(repetitionService).addToReviewPlan(7L, 21L);
        verify(eventService).recordQuestionAnswer(anyLong(), any(Question.class),
                org.mockito.ArgumentMatchers.eq("STAGE_ASSESSMENT_ANSWERED"),
                org.mockito.ArgumentMatchers.eq("STAGE_ASSESSMENT"), anyLong(),
                org.mockito.ArgumentMatchers.eq(true), any());
    }

    @Test
    void listsOnlyCompletedAssessmentsForJoinedCourse() {
        when(assessmentMapper.countUserCourse(7L, 10L)).thenReturn(1L);
        CourseStageAssessment item = completedAssessment();
        Page<CourseStageAssessment> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(item));
        when(assessmentMapper.selectCompletedPage(any(), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(10L))).thenReturn(page);
        when(assessmentQuestionMapper.selectSourcesByAssessmentIds(List.of(51L))).thenReturn(List.of(snapshot()));

        Page<CourseStageAssessmentSummaryVO> result = service.listCompleted(7L, 10L, 1, 10);

        assertEquals(1, result.getTotal());
        assertEquals(51L, result.getRecords().get(0).getId());
        assertEquals(1, result.getRecords().get(0).getCorrectCount());
        assertEquals(1, result.getRecords().get(0).getSourceComposition().getAiGeneratedCount());
    }

    @Test
    void returnsOwnedCompletedDetailAndRejectsOtherUsersAssessment() {
        CourseStageAssessment item = completedAssessment();
        when(assessmentMapper.selectOwned(51L, 7L)).thenReturn(item);
        when(assessmentQuestionMapper.selectByAssessmentId(51L)).thenReturn(List.of(snapshot()));

        CourseStageAssessmentVO detail = service.getCompleted(51L, 7L);

        assertEquals("A", detail.getQuestions().get(0).getCorrectAnswer());
        when(assessmentMapper.selectOwned(51L, 8L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getCompleted(51L, 8L));
    }

    private CourseStageAssessment completedAssessment() {
        CourseStageAssessment assessment = new CourseStageAssessment();
        assessment.setId(51L);
        assessment.setUserId(7L);
        assessment.setCourseId(10L);
        assessment.setStatus("COMPLETED");
        assessment.setSelectionStrategy("LEARNING_STATE_PRIORITY");
        assessment.setQuestionCount(1);
        assessment.setCorrectCount(1);
        assessment.setStartTime(LocalDateTime.of(2026, 8, 15, 10, 0));
        assessment.setCompleteTime(LocalDateTime.of(2026, 8, 15, 10, 5));
        return assessment;
    }

    private Question question() {
        Question question = new Question();
        question.setId(21L);
        question.setCourseId(10L);
        question.setContent("栈的访问顺序是？");
        question.setQuestionType("SINGLE_CHOICE");
        question.setScore(2);
        question.setAnalysis("后进先出");
        question.setSourceType("AI_GENERATED");
        question.setOriginQuestionId(20L);
        return question;
    }

    private List<QuestionOption> options() {
        QuestionOption option = new QuestionOption();
        option.setQuestionId(21L);
        option.setOptionLabel("A");
        option.setContent("后进先出");
        option.setIsCorrect(1);
        option.setSortOrder(1);
        return List.of(option);
    }

    private CourseStageAssessmentQuestion snapshot() {
        CourseStageAssessmentQuestion item = new CourseStageAssessmentQuestion();
        item.setId(61L);
        item.setAssessmentId(51L);
        item.setQuestionId(21L);
        item.setSortOrder(1);
        item.setQuestionType("SINGLE_CHOICE");
        item.setSourceTypeSnapshot("AI_GENERATED");
        item.setSourceCategorySnapshot("AI_GENERATED");
        item.setContentSnapshot("栈的访问顺序是？");
        item.setOptionsSnapshot("[{\"label\":\"A\",\"content\":\"后进先出\"}]");
        item.setCorrectAnswerSnapshot("A");
        item.setAnalysisSnapshot("后进先出");
        item.setScore(2);
        return item;
    }
}
