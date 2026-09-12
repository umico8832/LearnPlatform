package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.ExamLearningAnswerRequest;
import com.learnplatform.dto.ExamLearningAnswerResultVO;
import com.learnplatform.dto.ExamLearningSessionVO;
import com.learnplatform.entity.ExamLearningAnswer;
import com.learnplatform.entity.ExamLearningSession;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.ExamLearningAnswerMapper;
import com.learnplatform.mapper.ExamLearningSessionMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.UserCourseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamPaperLearningServiceTest {

    @Mock private ExamLearningSessionMapper sessionMapper;
    @Mock private ExamLearningAnswerMapper learningAnswerMapper;
    @Mock private ExamPaperMapper examPaperMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private UserCourseMapper userCourseMapper;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private SpacedRepetitionService spacedRepetitionService;
    @Mock private CacheEvictService cacheEvictService;
    @Mock private CourseLearningEventService courseLearningEventService;
    private ExamPaperLearningService service;

    @BeforeEach
    void setUp() {
        ExamPaperLearningContextService contextService = new ExamPaperLearningContextService(
                examPaperMapper, examQuestionMapper, questionMapper, questionOptionMapper, userCourseMapper);
        service = new ExamPaperLearningService(sessionMapper, learningAnswerMapper, contextService,
                new AnswerEvaluator(), wrongQuestionService, spacedRepetitionService,
                cacheEvictService, courseLearningEventService);
    }

    @Test
    void startsCoursePaperSessionWithoutLeakingCorrectOption() {
        AtomicReference<ExamLearningSession> saved = new AtomicReference<>();
        stubEligiblePaper();
        when(sessionMapper.selectOne(any())).thenReturn(null);
        when(sessionMapper.insert(any())).thenAnswer(invocation -> {
            ExamLearningSession session = invocation.getArgument(0);
            session.setId(30L);
            saved.set(session);
            return 1;
        });
        when(sessionMapper.selectById(30L)).thenAnswer(invocation -> saved.get());
        when(learningAnswerMapper.selectList(any())).thenReturn(List.of());

        ExamLearningSessionVO result = service.startSession(2L, 7L);

        assertEquals(30L, result.getId());
        assertEquals(10L, result.getCurrentQuestionId());
        assertEquals("1(1)", result.getQuestions().get(0).getDisplayNumber());
        assertEquals(null, result.getQuestions().get(0).getOptions().get(0).getIsCorrect());
    }

    @Test
    void rejectsAnswerOutsideLearningPaper() {
        stubActiveSession();
        stubEligiblePaper();
        ExamLearningAnswerRequest request = request(99L, "A");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submitAnswer(30L, request, 7L));

        assertEquals("提交内容包含非本试卷题目", exception.getMessage());
    }

    @Test
    void gradesLearningAnswerAndWritesUnifiedCourseFact() {
        stubActiveSession();
        stubEligiblePaper();
        when(learningAnswerMapper.selectCount(any())).thenReturn(0L);
        when(learningAnswerMapper.insert(any())).thenAnswer(invocation -> {
            ExamLearningAnswer answer = invocation.getArgument(0);
            answer.setId(81L);
            return 1;
        });

        ExamLearningAnswerResultVO result = service.submitAnswer(30L, request(10L, "A"), 7L);

        assertTrue(result.getCorrect());
        assertEquals(5, result.getScore());
        assertEquals("A", result.getCorrectAnswer());
        ArgumentCaptor<ExamLearningAnswer> captor = ArgumentCaptor.forClass(ExamLearningAnswer.class);
        verify(learningAnswerMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getAttemptNo());
        verify(courseLearningEventService).recordQuestionAnswer(eq(7L), any(Question.class),
                eq("PAPER_LEARNING_ANSWERED"), eq("PAPER_LEARNING"), eq(81L), eq(true),
                eq(captor.getValue().getCreateTime()));
        verify(wrongQuestionService).removeOnCorrect(7L, 10L);
        verify(spacedRepetitionService).addToReviewPlan(7L, 10L);
    }

    @Test
    void subjectiveLearningAnswerUsesSelfReviewWithoutFakeScore() {
        stubActiveSession();
        stubEligiblePaper();
        Question subjective = question();
        subjective.setQuestionType("SHORT_ANSWER");
        subjective.setAnalysis("分步参考答案");
        when(questionMapper.selectById(10L)).thenReturn(subjective);
        when(questionOptionMapper.selectList(any())).thenReturn(List.of());
        when(learningAnswerMapper.selectCount(any())).thenReturn(0L);

        ExamLearningAnswerResultVO result = service.submitAnswer(30L, request(10L, "算法作答"), 7L);

        assertNull(result.getCorrect());
        assertNull(result.getScore());
        assertEquals("分步参考答案", result.getAnalysis());
    }

    @Test
    void refusesToCompleteBeforeEveryQuestionHasAnAttempt() {
        stubActiveSession();
        stubEligiblePaper();
        when(learningAnswerMapper.selectList(any())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.completeSession(30L, 7L));

        assertEquals("完成本轮学习前请先作答全部题目", exception.getMessage());
    }

    @Test
    void completesSessionAfterEveryQuestionHasAnAttempt() {
        stubActiveSession();
        stubEligiblePaper();
        ExamLearningAnswer answer = answeredQuestion();
        when(learningAnswerMapper.selectList(any())).thenReturn(List.of(answer));

        ExamLearningSessionVO result = service.completeSession(30L, 7L);

        assertEquals(1, result.getStatus());
        assertEquals(1, result.getAnsweredQuestionCount());
        assertEquals(1, result.getCorrectQuestionCount());
        ArgumentCaptor<ExamLearningSession> captor = ArgumentCaptor.forClass(ExamLearningSession.class);
        verify(sessionMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getStatus());
        assertEquals(null, captor.getValue().getActiveSessionKey());
        assertTrue(captor.getValue().getCompleteTime() != null);
    }

    @Test
    void rejectsAnotherUsersLearningSession() {
        ExamLearningSession session = new ExamLearningSession();
        session.setId(30L);
        session.setUserId(8L);
        when(sessionMapper.selectById(30L)).thenReturn(session);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getSession(30L, 7L));

        assertEquals("无权访问该学习会话", exception.getMessage());
    }

    private void stubActiveSession() {
        ExamLearningSession session = new ExamLearningSession();
        session.setId(30L);
        session.setUserId(7L);
        session.setExamPaperId(2L);
        session.setStatus(0);
        session.setCurrentQuestionId(10L);
        when(sessionMapper.selectByIdForUpdate(30L)).thenReturn(session);
    }

    private void stubEligiblePaper() {
        when(examPaperMapper.selectById(2L)).thenReturn(paper());
        when(userCourseMapper.selectCount(any())).thenReturn(1L);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion()));
        when(questionMapper.selectById(10L)).thenReturn(question());
        lenient().when(questionOptionMapper.selectList(any())).thenReturn(List.of(option()));
    }

    private ExamPaper paper() {
        ExamPaper paper = new ExamPaper();
        paper.setId(2L);
        paper.setTitle("结构化试卷");
        paper.setCourseId(20L);
        paper.setPaperType("OFFICIAL_EXAM");
        paper.setStatus(1);
        return paper;
    }

    private ExamQuestion examQuestion() {
        ExamQuestion item = new ExamQuestion();
        item.setExamPaperId(2L);
        item.setQuestionId(10L);
        item.setSortOrder(1);
        item.setScore(5);
        item.setDisplayNumber("1(1)");
        return item;
    }

    private Question question() {
        Question question = new Question();
        question.setId(10L);
        question.setCourseId(20L);
        question.setStatus(1);
        question.setQuestionType("SINGLE_CHOICE");
        question.setContent("正确选项是？");
        question.setAnalysis("解析");
        return question;
    }

    private QuestionOption option() {
        QuestionOption option = new QuestionOption();
        option.setId(100L);
        option.setQuestionId(10L);
        option.setOptionLabel("A");
        option.setContent("正确");
        option.setIsCorrect(1);
        option.setSortOrder(1);
        return option;
    }

    private ExamLearningAnswer answeredQuestion() {
        ExamLearningAnswer answer = new ExamLearningAnswer();
        answer.setId(81L);
        answer.setSessionId(30L);
        answer.setQuestionId(10L);
        answer.setAttemptNo(1);
        answer.setUserAnswer("A");
        answer.setIsCorrect(1);
        answer.setScore(5);
        return answer;
    }

    private ExamLearningAnswerRequest request(Long questionId, String answer) {
        ExamLearningAnswerRequest request = new ExamLearningAnswerRequest();
        request.setQuestionId(questionId);
        request.setUserAnswer(answer);
        return request;
    }
}
