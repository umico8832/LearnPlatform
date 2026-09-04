package com.learnplatform.service.exam;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiExamCandidateLoaderTest {

    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private PracticeRecordMapper practiceRecordMapper;
    @Mock private WrongQuestionMapper wrongQuestionMapper;

    private AiExamCandidateLoader loader;

    @BeforeEach
    void setUp() {
        loader = new AiExamCandidateLoader(questionMapper, questionKnowledgePointMapper, knowledgePointMapper,
                practiceRecordMapper, wrongQuestionMapper);
    }

    @Test
    void loadAvailableQuestionsReturnsMapperCandidatesForTheRequestedCourse() {
        Question question = new Question();
        question.setId(1L);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));

        assertEquals(List.of(question), loader.loadAvailableQuestions(8L));

        verify(questionMapper).selectList(any());
    }

    @Test
    void loadAvailableQuestionsRetainsEmptyCandidateFailure() {
        when(questionMapper.selectList(any())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> loader.loadAvailableQuestions(null));

        assertEquals("题库中没有可用的题目", exception.getMessage());
    }

    @Test
    void loadUserDifficultyAccuracyUsesTheSamePerQuestionHistoryCalculation() {
        PracticeRecord correct = record(1L, 1);
        PracticeRecord incorrect = record(1L, 0);
        PracticeRecord anotherDifficulty = record(2L, 1);
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(correct, incorrect, anotherDifficulty));
        when(questionMapper.selectById(1L)).thenReturn(question(3));
        when(questionMapper.selectById(2L)).thenReturn(question(5));

        Map<Integer, Double> accuracy = loader.loadUserDifficultyAccuracy(7L);

        assertEquals(0.5, accuracy.get(3));
        assertEquals(1.0, accuracy.get(5));
    }

    private PracticeRecord record(Long questionId, int correct) {
        PracticeRecord record = new PracticeRecord();
        record.setQuestionId(questionId);
        record.setIsCorrect(correct);
        return record;
    }

    private Question question(int difficulty) {
        Question question = new Question();
        question.setDifficulty(difficulty);
        return question;
    }
}
