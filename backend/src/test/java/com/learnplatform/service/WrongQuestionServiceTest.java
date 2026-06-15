package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WrongQuestionServiceTest {

    @Mock private WrongQuestionMapper wrongQuestionMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private CacheEvictService cacheEvictService;

    private WrongQuestionService wrongQuestionService;

    @BeforeEach
    void setUp() {
        wrongQuestionService = new WrongQuestionService(wrongQuestionMapper, questionMapper, courseMapper, cacheEvictService);
    }

    @Test
    void addWrongQuestionCreatesRecordWhenAbsent() {
        when(wrongQuestionMapper.selectOne(any())).thenReturn(null);

        wrongQuestionService.addWrongQuestion(7L, 10L, "B");

        ArgumentCaptor<WrongQuestion> captor = ArgumentCaptor.forClass(WrongQuestion.class);
        verify(wrongQuestionMapper).insert(captor.capture());
        WrongQuestion record = captor.getValue();
        assertEquals(7L, record.getUserId());
        assertEquals(10L, record.getQuestionId());
        assertEquals(1, record.getWrongCount());
        assertEquals(0, record.getMasteryLevel());
        assertEquals("B", record.getLastWrongAnswer());
        assertEquals(0, record.getDeleted());
        verify(wrongQuestionMapper, never()).updateById(any());
    }

    @Test
    void addWrongQuestionIncrementsExistingRecord() {
        WrongQuestion existing = wrongQuestion(1L, 7L, 10L, 2, 1, "A");
        when(wrongQuestionMapper.selectOne(any())).thenReturn(existing);

        wrongQuestionService.addWrongQuestion(7L, 10L, "C");

        assertEquals(3, existing.getWrongCount());
        assertEquals(1, existing.getMasteryLevel());
        assertEquals("C", existing.getLastWrongAnswer());
        verify(wrongQuestionMapper).updateById(existing);
        verify(wrongQuestionMapper, never()).insert(any());
    }

    @Test
    void addWrongQuestionResetsMasteredRecordToUnmastered() {
        WrongQuestion existing = wrongQuestion(1L, 7L, 10L, 2, 2, "A");
        when(wrongQuestionMapper.selectOne(any())).thenReturn(existing);

        wrongQuestionService.addWrongQuestion(7L, 10L, "D");

        assertEquals(3, existing.getWrongCount());
        assertEquals(0, existing.getMasteryLevel());
        assertEquals("D", existing.getLastWrongAnswer());
        verify(wrongQuestionMapper).updateById(existing);
    }

    @Test
    void removeOnCorrectDeletesExistingWrongQuestion() {
        WrongQuestion existing = wrongQuestion(3L, 7L, 10L, 1, 0, "B");
        when(wrongQuestionMapper.selectOne(any())).thenReturn(existing);

        wrongQuestionService.removeOnCorrect(7L, 10L);

        verify(wrongQuestionMapper).deleteById(3L);
    }

    @Test
    void updateMasteryLevelRejectsOtherUsersRecord() {
        when(wrongQuestionMapper.selectById(3L)).thenReturn(wrongQuestion(3L, 8L, 10L, 1, 0, "B"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> wrongQuestionService.updateMasteryLevel(3L, 7L, 2));

        assertEquals("错题记录不存在", exception.getMessage());
        verify(wrongQuestionMapper, never()).updateById(any());
    }

    @Test
    void removeWrongQuestionRejectsMissingRecord() {
        when(wrongQuestionMapper.selectById(3L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> wrongQuestionService.removeWrongQuestion(3L, 7L));

        assertEquals("错题记录不存在", exception.getMessage());
        verify(wrongQuestionMapper, never()).deleteById(any(Long.class));
    }

    private WrongQuestion wrongQuestion(Long id, Long userId, Long questionId,
                                        Integer wrongCount, Integer masteryLevel,
                                        String lastWrongAnswer) {
        WrongQuestion wrongQuestion = new WrongQuestion();
        wrongQuestion.setId(id);
        wrongQuestion.setUserId(userId);
        wrongQuestion.setQuestionId(questionId);
        wrongQuestion.setWrongCount(wrongCount);
        wrongQuestion.setMasteryLevel(masteryLevel);
        wrongQuestion.setLastWrongAnswer(lastWrongAnswer);
        return wrongQuestion;
    }
}
