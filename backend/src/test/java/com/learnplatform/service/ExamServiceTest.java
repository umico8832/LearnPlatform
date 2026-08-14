package com.learnplatform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.ExamTimedOutException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.entity.ExamAnswer;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.ExamRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.ExamAnswerMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    @Mock private ExamRecordMapper examRecordMapper;
    @Mock private ExamAnswerMapper examAnswerMapper;
    @Mock private ExamPaperMapper examPaperMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private CacheEvictService cacheEvictService;
    private ExamService examService;

    @BeforeEach
    void setUp() {
        examService = new ExamService(examRecordMapper, examAnswerMapper, examPaperMapper,
                examQuestionMapper, questionMapper, questionOptionMapper,
                null, new AnswerEvaluator(), cacheEvictService);
    }

    @Test
    void rejectsQuestionOutsidePaper() {
        stubActiveExam();
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion(10L, 5)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examService.submitExam(request(answer(99L, "A")), 7L));

        assertEquals("提交内容包含非本试卷题目", exception.getMessage());
    }

    @Test
    void rejectsDuplicateQuestion() {
        stubActiveExam();
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion(10L, 5)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examService.submitExam(request(answer(10L, "A"), answer(10L, "A")), 7L));

        assertEquals("同一道题不能重复提交", exception.getMessage());
    }

    @Test
    void marksExpiredExamAsTimedOut() {
        ExamRecord record = record(LocalDateTime.now().minusMinutes(61));
        when(examRecordMapper.selectByIdForUpdate(1L)).thenReturn(record);
        when(examPaperMapper.selectById(2L)).thenReturn(paper());

        ExamTimedOutException exception = assertThrows(ExamTimedOutException.class,
                () -> examService.submitExam(request(answer(10L, "A")), 7L));

        assertEquals("考试已超时", exception.getMessage());
        assertEquals(2, record.getStatus());
        verify(examRecordMapper).updateById(record);
    }

    @Test
    void reusesActiveExamBeforeItsServerDeadline() {
        ExamRecord active = record(LocalDateTime.now().minusMinutes(10));
        active.setActiveExamKey("EXAM:7:2");
        when(examPaperMapper.selectById(2L)).thenReturn(publishedPaper());
        when(examRecordMapper.selectByActiveExamKey("EXAM:7:2")).thenReturn(active);
        when(examRecordMapper.selectByIdForUpdate(1L)).thenReturn(active);

        ExamRecordVO result = examService.startExam(2L, 7L);

        assertEquals(1L, result.getId());
        assertEquals(0, result.getStatus());
        verify(examRecordMapper, never()).insert(any());
    }

    @Test
    void expiresStaleActiveExamBeforeStartingANewAttempt() {
        ExamRecord stale = record(LocalDateTime.now().minusMinutes(61));
        stale.setActiveExamKey("EXAM:7:2");
        when(examPaperMapper.selectById(2L)).thenReturn(publishedPaper());
        when(examRecordMapper.selectByActiveExamKey("EXAM:7:2")).thenReturn(stale);
        when(examRecordMapper.selectByIdForUpdate(1L)).thenReturn(stale);
        doAnswer(invocation -> {
            ExamRecord inserted = invocation.getArgument(0);
            inserted.setId(2L);
            return 1;
        }).when(examRecordMapper).insert(any(ExamRecord.class));

        ExamRecordVO result = examService.startExam(2L, 7L);

        assertEquals(2L, result.getId());
        assertEquals(2, stale.getStatus());
        assertNull(stale.getActiveExamKey());
        verify(examRecordMapper).updateById(stale);
        ArgumentCaptor<ExamRecord> insertedCaptor = ArgumentCaptor.forClass(ExamRecord.class);
        verify(examRecordMapper).insert(insertedCaptor.capture());
        assertEquals("EXAM:7:2", insertedCaptor.getValue().getActiveExamKey());
    }

    @Test
    void recoversConcurrentActiveExamWhenUniqueKeyWinsTheRace() {
        ExamRecord concurrent = record(LocalDateTime.now());
        concurrent.setActiveExamKey("EXAM:7:2");
        when(examPaperMapper.selectById(2L)).thenReturn(publishedPaper());
        when(examRecordMapper.selectByActiveExamKey("EXAM:7:2")).thenReturn(null);
        when(examRecordMapper.selectByActiveExamKeyForUpdate("EXAM:7:2")).thenReturn(concurrent);
        doThrow(new DuplicateKeyException("duplicate active exam"))
                .when(examRecordMapper).insert(any(ExamRecord.class));

        ExamRecordVO result = examService.startExam(2L, 7L);

        assertEquals(1L, result.getId());
    }

    @Test
    void sessionReadPersistsTimeoutAndReturnsAuthoritativeClock() {
        ExamRecord stale = record(LocalDateTime.now().minusMinutes(61));
        stale.setActiveExamKey("EXAM:7:2");
        when(examRecordMapper.selectByIdForUpdate(1L)).thenReturn(stale);
        when(examPaperMapper.selectById(2L)).thenReturn(publishedPaper());

        ExamRecordVO result = examService.getExamSession(1L, 7L);

        assertEquals(2, result.getStatus());
        assertNotNull(result.getDeadline());
        assertNotNull(result.getServerTime());
        assertNull(result.getAnswers());
        assertNull(stale.getActiveExamKey());
        verify(examRecordMapper).updateById(stale);
    }

    @Test
    void sessionReadRejectsAnotherUsersRecord() {
        when(examRecordMapper.selectByIdForUpdate(1L)).thenReturn(record(LocalDateTime.now()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examService.getExamSession(1L, 8L));

        assertEquals(ResultCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    void resultCannotExposeAnswersBeforeExamCompletion() {
        when(examRecordMapper.selectById(1L)).thenReturn(record(LocalDateTime.now()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examService.getExamResult(1L, 7L));

        assertEquals("考试尚未完成", exception.getMessage());
    }

    @Test
    void shortAnswerSubmissionWaitsForManualReviewInsteadOfKeywordGrading() {
        ExamRecord record = record(LocalDateTime.now());
        when(examRecordMapper.selectByIdForUpdate(1L)).thenReturn(record);
        when(examRecordMapper.selectById(1L)).thenReturn(record);
        when(examPaperMapper.selectById(2L)).thenReturn(paper());
        ExamQuestion paperQuestion = examQuestion(10L, 13);
        paperQuestion.setSortOrder(1);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(paperQuestion));
        Question question = new Question();
        question.setId(10L);
        question.setQuestionType("SHORT_ANSWER");
        when(questionMapper.selectById(10L)).thenReturn(question);
        when(questionOptionMapper.selectList(any())).thenReturn(List.of());
        when(examAnswerMapper.selectList(any())).thenReturn(List.of());

        ExamRecordVO result = examService.submitExam(
                request(answer(10L, "先中序遍历，再记录最小差值与全部并列结点")), 7L);

        ArgumentCaptor<ExamAnswer> answerCaptor = ArgumentCaptor.forClass(ExamAnswer.class);
        verify(examAnswerMapper).insert(answerCaptor.capture());
        assertNull(answerCaptor.getValue().getIsCorrect());
        assertNull(answerCaptor.getValue().getScore());
        assertEquals(3, result.getStatus());
        assertEquals(0, result.getScore());
    }

    @Test
    void preservesPaperSourceAndOriginalQuestionNumberingInResult() {
        ExamQuestion examQuestion = examQuestion(10L, 5);
        examQuestion.setSortOrder(1);
        examQuestion.setSectionTitle("一、单项选择题");
        examQuestion.setMajorQuestionNumber("一");
        examQuestion.setMinorQuestionNumber("1");
        examQuestion.setSubquestionNumber("(1)");
        examQuestion.setDisplayNumber("一、1（1）");

        ExamAnswer answer = new ExamAnswer();
        answer.setQuestionId(10L);
        answer.setUserAnswer("A");
        answer.setIsCorrect(1);
        answer.setScore(5);

        ExamRecord completed = record(LocalDateTime.now());
        completed.setStatus(1);
        when(examRecordMapper.selectById(1L)).thenReturn(completed);
        when(examPaperMapper.selectById(2L)).thenReturn(paperWithSourceMetadata());
        when(examAnswerMapper.selectList(any())).thenReturn(List.of(answer));
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion));

        ExamRecordVO result = examService.getExamResult(1L, 7L);

        assertPaperSourceMetadata(result);

        ExamRecordVO.ExamAnswerVO answerResult = result.getAnswers().get(0);
        assertEquals("一、单项选择题", answerResult.getSectionTitle());
        assertEquals("一", answerResult.getMajorQuestionNumber());
        assertEquals("1", answerResult.getMinorQuestionNumber());
        assertEquals("(1)", answerResult.getSubquestionNumber());
        assertEquals("一、1（1）", answerResult.getDisplayNumber());
    }

    @Test
    void preservesPaperSourceMetadataInExamRecordList() {
        Page<ExamRecord> records = new Page<>(1, 10, 1);
        records.setRecords(List.of(record(LocalDateTime.now())));
        when(examRecordMapper.selectPage(any(), any())).thenReturn(records);
        when(examPaperMapper.selectById(2L)).thenReturn(paperWithSourceMetadata());

        ExamRecordVO result = examService.getExamList(7L, 1, 10).getRecords().get(0);

        assertPaperSourceMetadata(result);
    }

    private void stubActiveExam() {
        when(examRecordMapper.selectByIdForUpdate(1L)).thenReturn(record(LocalDateTime.now()));
        when(examPaperMapper.selectById(2L)).thenReturn(paper());
    }

    private ExamRecord record(LocalDateTime startTime) {
        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setUserId(7L);
        record.setExamPaperId(2L);
        record.setStartTime(startTime);
        record.setStatus(0);
        return record;
    }

    private ExamPaper paper() {
        ExamPaper paper = new ExamPaper();
        paper.setId(2L);
        paper.setDuration(60);
        paper.setTotalScore(5);
        return paper;
    }

    private ExamPaper publishedPaper() {
        ExamPaper paper = paper();
        paper.setStatus(1);
        return paper;
    }

    private ExamPaper paperWithSourceMetadata() {
        ExamPaper paper = paper();
        paper.setCourseId(3L);
        paper.setPaperType("OFFICIAL_EXAM");
        paper.setExamName("全国硕士研究生招生考试");
        paper.setExamYear(2025);
        paper.setSourceReference("https://example.test/official-paper");
        paper.setSourceVerified(true);
        return paper;
    }

    private ExamQuestion examQuestion(Long questionId, int score) {
        ExamQuestion question = new ExamQuestion();
        question.setQuestionId(questionId);
        question.setScore(score);
        return question;
    }

    private ExamSubmitRequest request(ExamSubmitRequest.AnswerItem... answers) {
        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setExamRecordId(1L);
        request.setAnswers(List.of(answers));
        return request;
    }

    private ExamSubmitRequest.AnswerItem answer(Long questionId, String userAnswer) {
        ExamSubmitRequest.AnswerItem answer = new ExamSubmitRequest.AnswerItem();
        answer.setQuestionId(questionId);
        answer.setUserAnswer(userAnswer);
        return answer;
    }

    private void assertPaperSourceMetadata(ExamRecordVO result) {
        assertEquals(3L, result.getCourseId());
        assertEquals("OFFICIAL_EXAM", result.getPaperType());
        assertEquals("全国硕士研究生招生考试", result.getExamName());
        assertEquals(2025, result.getExamYear());
        assertEquals("https://example.test/official-paper", result.getSourceReference());
        assertEquals(true, result.getSourceVerified());
    }
}
