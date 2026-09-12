package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.dto.PracticeResultVO;
import com.learnplatform.dto.PracticeSubmitRequest;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.UserFavoriteQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.UserFavoriteQuestionMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeServiceTest {

    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private PracticeRecordMapper practiceRecordMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private WrongQuestionMapper wrongQuestionMapper;
    @Mock private UserFavoriteQuestionMapper userFavoriteQuestionMapper;
    @Mock private CacheEvictService cacheEvictService;
    @Mock private SpacedRepetitionService spacedRepetitionService;
    @Mock private CourseLearningEventService courseLearningEventService;
    private RecordingWrongQuestionService wrongQuestionService;
    private PracticeService practiceService;

    @BeforeEach
    void setUp() {
        wrongQuestionService = new RecordingWrongQuestionService();
        PracticeQuestionQueryService questionQueryService = new PracticeQuestionQueryService(
                questionMapper, questionOptionMapper, questionKnowledgePointMapper, courseMapper,
                knowledgePointMapper, wrongQuestionMapper, userFavoriteQuestionMapper);
        PracticeHistoryService historyService = new PracticeHistoryService(
                practiceRecordMapper, questionMapper, courseMapper);
        PracticeAnswerService answerService = new PracticeAnswerService(
                questionMapper, questionOptionMapper, practiceRecordMapper, wrongQuestionService,
                new AnswerEvaluator(), cacheEvictService, spacedRepetitionService,
                courseLearningEventService);
        practiceService = new PracticeService(questionQueryService, historyService, answerService);
    }

    @Test
    void submitCorrectAnswerSavesRecordAndRemovesWrongQuestion() {
        when(questionMapper.selectById(10L)).thenReturn(singleChoiceQuestion());
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(
                option("A", "正确选项", 1),
                option("B", "错误选项", 0)
        ));

        PracticeResultVO result = practiceService.submitAnswer(request(10L, " A ", 18), 7L);

        assertTrue(result.getCorrect());
        assertEquals("A", result.getCorrectAnswer());
        assertEquals("A", result.getUserAnswer());
        assertEquals("题目解析", result.getAnalysis());
        assertEquals(5, result.getScore());

        ArgumentCaptor<PracticeRecord> recordCaptor = ArgumentCaptor.forClass(PracticeRecord.class);
        verify(practiceRecordMapper).insert(recordCaptor.capture());
        PracticeRecord record = recordCaptor.getValue();
        assertEquals(7L, record.getUserId());
        assertEquals(10L, record.getQuestionId());
        assertEquals("A", record.getUserAnswer());
        assertEquals(1, record.getIsCorrect());
        assertEquals(18, record.getAnswerTime());

        assertEquals(7L, wrongQuestionService.removedUserId);
        assertEquals(10L, wrongQuestionService.removedQuestionId);
        assertEquals(0, wrongQuestionService.addWrongQuestionCalls);
    }

    @Test
    void submitWrongAnswerSavesRecordAndAddsWrongQuestion() {
        when(questionMapper.selectById(10L)).thenReturn(singleChoiceQuestion());
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(
                option("A", "正确选项", 1),
                option("B", "错误选项", 0)
        ));

        PracticeResultVO result = practiceService.submitAnswer(request(10L, "B", 21), 7L);

        assertFalse(result.getCorrect());
        assertEquals("A", result.getCorrectAnswer());
        assertEquals("B", result.getUserAnswer());

        ArgumentCaptor<PracticeRecord> recordCaptor = ArgumentCaptor.forClass(PracticeRecord.class);
        verify(practiceRecordMapper).insert(recordCaptor.capture());
        assertEquals(0, recordCaptor.getValue().getIsCorrect());

        assertEquals(7L, wrongQuestionService.addedUserId);
        assertEquals(10L, wrongQuestionService.addedQuestionId);
        assertEquals("B", wrongQuestionService.addedUserAnswer);
        assertEquals(0, wrongQuestionService.removeOnCorrectCalls);
    }

    @Test
    void submitAnswerRejectsMissingQuestionId() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> practiceService.submitAnswer(request(null, "A", 10), 7L));

        assertEquals("题目ID不能为空", exception.getMessage());
        verify(practiceRecordMapper, never()).insert(any());
    }

    @Test
    void submitAnswerRejectsBlankAnswer() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> practiceService.submitAnswer(request(10L, "   ", 10), 7L));

        assertEquals("答案不能为空", exception.getMessage());
        verify(practiceRecordMapper, never()).insert(any());
    }

    @Test
    void submitAnswerRejectsMissingQuestion() {
        when(questionMapper.selectById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> practiceService.submitAnswer(request(404L, "A", 10), 7L));

        assertEquals("题目不存在", exception.getMessage());
        verify(practiceRecordMapper, never()).insert(any());
    }

    @Test
    void favoritePracticeReturnsPracticeQuestionAndHidesAnswer() {
        UserFavoriteQuestion favorite = new UserFavoriteQuestion();
        favorite.setUserId(7L);
        favorite.setQuestionId(10L);
        when(userFavoriteQuestionMapper.selectList(any())).thenReturn(List.of(favorite));
        when(questionMapper.selectList(any())).thenReturn(List.of(singleChoiceQuestion()));
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(
                option("A", "正确选项", 1),
                option("B", "错误选项", 0)
        ));
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of());

        List<QuestionVO> questions = practiceService.getFavoritePractice(7L, 10, 10L);

        assertEquals(1, questions.size());
        QuestionVO question = questions.get(0);
        assertEquals(10L, question.getId());
        assertEquals("SINGLE_CHOICE", question.getQuestionType());
        assertEquals(null, question.getAnalysis());
        assertEquals(2, question.getOptions().size());
        assertEquals(0, question.getOptions().get(0).getIsCorrect());
        assertEquals(0, question.getOptions().get(1).getIsCorrect());
    }

    private PracticeSubmitRequest request(Long questionId, String userAnswer, Integer answerTime) {
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setQuestionId(questionId);
        request.setUserAnswer(userAnswer);
        request.setAnswerTime(answerTime);
        return request;
    }

    private Question singleChoiceQuestion() {
        Question question = new Question();
        question.setId(10L);
        question.setQuestionType("SINGLE_CHOICE");
        question.setAnalysis("题目解析");
        question.setScore(5);
        return question;
    }

    private QuestionOption option(String label, String content, Integer isCorrect) {
        QuestionOption option = new QuestionOption();
        option.setOptionLabel(label);
        option.setContent(content);
        option.setIsCorrect(isCorrect);
        return option;
    }

    private static class RecordingWrongQuestionService extends WrongQuestionService {
        private int addWrongQuestionCalls;
        private Long addedUserId;
        private Long addedQuestionId;
        private String addedUserAnswer;
        private int removeOnCorrectCalls;
        private Long removedUserId;
        private Long removedQuestionId;

        RecordingWrongQuestionService() {
            super(null, null, null, null, null);
        }

        @Override
        public void addWrongQuestion(Long userId, Long questionId, String userAnswer) {
            addWrongQuestionCalls++;
            addedUserId = userId;
            addedQuestionId = questionId;
            addedUserAnswer = userAnswer;
        }

        @Override
        public void removeOnCorrect(Long userId, Long questionId) {
            removeOnCorrectCalls++;
            removedUserId = userId;
            removedQuestionId = questionId;
        }
    }
}
