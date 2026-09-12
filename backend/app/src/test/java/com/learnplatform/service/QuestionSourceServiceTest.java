package com.learnplatform.service;

import com.learnplatform.dto.QuestionReReviewRequest;
import com.learnplatform.dto.QuestionReviewRecordVO;
import com.learnplatform.dto.QuestionSourceStatsVO;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewRecord;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionReviewRecordMapper;
import com.learnplatform.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionSourceServiceTest {

    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionReviewRecordMapper reviewRecordMapper;
    @Mock private UserMapper userMapper;
    @Mock private QuestionVersionService questionVersionService;

    private QuestionSourceService service;

    @BeforeEach
    void setUp() {
        // Init MyBatis-Plus lambda cache for Question entity
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Question.class);
        service = new QuestionSourceService(questionMapper, reviewRecordMapper, userMapper, questionVersionService);
    }

    @Test
    void setSource_updatesQuestionSourceTypeAndNextReviewTime() {
        Question question = new Question();
        question.setId(1L);
        when(questionMapper.selectById(1L)).thenReturn(question);

        service.setSource(1L, "SUBMISSION", "submission:10");

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).updateById(captor.capture());
        Question updated = captor.getValue();
        assertEquals("SUBMISSION", updated.getSourceType());
        assertEquals("submission:10", updated.getSourceReference());
        assertNotNull(updated.getNextReviewTime());
        assertEquals(0, updated.getReviewRounds());
    }

    @Test
    void recordInitialReview_insertsRecordAndUpdatesQuestion() {
        Question question = new Question();
        question.setId(1L);
        question.setReviewRounds(0);
        when(questionMapper.selectById(1L)).thenReturn(question);

        service.recordInitialReview(1L, 2L, "入库初审通过");

        verify(reviewRecordMapper).insert(any(QuestionReviewRecord.class));
        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getReviewRounds());
        assertNotNull(captor.getValue().getLastReviewTime());
    }

    @Test
    void performReReview_approve_updatesQuestionAndInsertsRecord() {
        Question question = new Question();
        question.setId(1L);
        question.setContent("原题干");
        question.setDifficulty(3);
        question.setReviewRounds(0);
        when(questionMapper.selectById(1L)).thenReturn(question);

        QuestionReReviewRequest request = new QuestionReReviewRequest();
        request.setAction("APPROVE");
        request.setComment("复审通过");

        QuestionReviewRecordVO vo = service.performReReview(1L, request, 2L);

        assertNotNull(vo);
        assertEquals("APPROVE", vo.getAction());
        assertEquals("REGULAR", vo.getReviewType());
        verify(reviewRecordMapper).insert(any(QuestionReviewRecord.class));
        verify(questionMapper).updateById(any(Question.class));
    }

    @Test
    void performReReview_revise_updatesContent() {
        Question question = new Question();
        question.setId(1L);
        question.setContent("原题干");
        question.setDifficulty(3);
        question.setReviewRounds(0);
        when(questionMapper.selectById(1L)).thenReturn(question);

        QuestionReReviewRequest request = new QuestionReReviewRequest();
        request.setAction("REVISE");
        request.setNewContent("修订后题干");
        request.setNewDifficulty(4);
        request.setComment("内容有误");

        service.performReReview(1L, request, 2L);

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).updateById(captor.capture());
        assertEquals("修订后题干", captor.getValue().getContent());
        assertEquals(4, captor.getValue().getDifficulty());
    }

    @Test
    void performReReview_reject_setsStatusToDisabled() {
        Question question = new Question();
        question.setId(1L);
        question.setContent("原题干");
        question.setDifficulty(3);
        question.setReviewRounds(0);
        when(questionMapper.selectById(1L)).thenReturn(question);

        QuestionReReviewRequest request = new QuestionReReviewRequest();
        request.setAction("REJECT");
        request.setComment("过时题目");

        service.performReReview(1L, request, 2L);

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    void performReReview_invalidAction_throwsException() {
        Question question = new Question();
        question.setId(1L);
        when(questionMapper.selectById(1L)).thenReturn(question);

        QuestionReReviewRequest request = new QuestionReReviewRequest();
        request.setAction("INVALID");
        request.setComment("test");

        assertThrows(Exception.class, () -> service.performReReview(1L, request, 2L));
    }

    @Test
    void getSourceStats_returnsAllSourceTypes() {
        when(questionMapper.selectList(any())).thenReturn(List.of());

        List<QuestionSourceStatsVO> stats = service.getSourceStats();

        assertEquals(5, stats.size());
        assertTrue(stats.stream().anyMatch(s -> "MANUAL".equals(s.getSourceType())));
        assertTrue(stats.stream().anyMatch(s -> "SUBMISSION".equals(s.getSourceType())));
        assertTrue(stats.stream().anyMatch(s -> "EXCEL_IMPORT".equals(s.getSourceType())));
        assertTrue(stats.stream().anyMatch(s -> "MARKDOWN_IMPORT".equals(s.getSourceType())));
        assertTrue(stats.stream().anyMatch(s -> "AI_GENERATED".equals(s.getSourceType())));
    }

    @Test
    void getOverdueReviewsMapsPersistenceEntitiesToVo() {
        Question question = new Question();
        question.setId(10L);
        question.setContent("待复审题目");
        question.setOwnerUserId(99L);
        Page<Question> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(question));
        when(questionMapper.selectPage(any(IPage.class), any())).thenReturn(page);

        Page<QuestionVO> result = service.getOverdueReviews(1, 10);

        assertEquals(10L, result.getRecords().getFirst().getId());
        assertEquals("待复审题目", result.getRecords().getFirst().getContent());
    }

    @Test
    void getReviewRecords_returnsRecordsWithReviewerName() {
        QuestionReviewRecord record = new QuestionReviewRecord();
        record.setId(1L);
        record.setQuestionId(10L);
        record.setReviewerId(2L);
        record.setReviewType("REGULAR");
        record.setAction("APPROVE");
        record.setComment("ok");
        when(reviewRecordMapper.selectList(any())).thenReturn(List.of(record));

        User reviewer = new User();
        reviewer.setId(2L);
        reviewer.setUsername("admin");
        reviewer.setNickname("管理员");
        when(userMapper.selectById(2L)).thenReturn(reviewer);

        List<QuestionReviewRecordVO> records = service.getReviewRecords(10L);

        assertEquals(1, records.size());
        assertEquals("管理员", records.get(0).getReviewerName());
    }
}
