package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.AiVariantQuestionVO;
import com.learnplatform.dto.AiVariantTrainingVO;
import com.learnplatform.entity.AiVariantQuestion;
import com.learnplatform.entity.AiVariantTraining;
import com.learnplatform.entity.QuestionAiAsset;
import com.learnplatform.mapper.AiVariantQuestionMapper;
import com.learnplatform.mapper.AiVariantTrainingMapper;
import com.learnplatform.mapper.QuestionAiAssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiVariantQuestionServiceTest {

    @Mock private QuestionAiAssetMapper questionAiAssetMapper;
    @Mock private AiVariantQuestionMapper aiVariantQuestionMapper;
    @Mock private AiVariantTrainingMapper aiVariantTrainingMapper;

    private AiVariantQuestionService service;

    @BeforeEach
    void setUp() {
        service = new AiVariantQuestionService(new ObjectMapper(), questionAiAssetMapper,
                aiVariantQuestionMapper, aiVariantTrainingMapper, new AnswerEvaluator());
    }

    @Test
    void saveGeneratedAssetSeparatesPublicContentFromPrivateAnswer() {
        when(questionAiAssetMapper.insert(any())).thenAnswer(invocation -> {
            QuestionAiAsset asset = invocation.getArgument(0);
            asset.setId(21L);
            return 1;
        });
        when(aiVariantQuestionMapper.insert(any())).thenAnswer(invocation -> {
            AiVariantQuestion question = invocation.getArgument(0);
            question.setId(31L);
            return 1;
        });

        QuestionAiAsset asset = service.saveGeneratedAsset(7L, "test-model", validJson());

        assertEquals(21L, asset.getId());
        assertFalse(asset.getContent().contains("B"));
        assertFalse(asset.getContent().contains("正确答案"));

        ArgumentCaptor<AiVariantQuestion> captor = ArgumentCaptor.forClass(AiVariantQuestion.class);
        verify(aiVariantQuestionMapper).insert(captor.capture());
        AiVariantQuestion privateQuestion = captor.getValue();
        assertEquals(21L, privateQuestion.getAssetId());
        assertEquals("B", privateQuestion.getCorrectAnswer());
        assertEquals("SINGLE_CHOICE", privateQuestion.getQuestionType());
    }

    @Test
    void getPublicQuestionDoesNotExposeAnswerOrAnalysis() {
        AiVariantQuestion question = structuredQuestion();
        when(aiVariantQuestionMapper.selectOne(any())).thenReturn(question);

        AiVariantQuestionVO result = service.getPublicQuestion(21L);

        assertNotNull(result);
        assertEquals("以下哪个描述正确？", result.getQuestionContent());
        assertEquals(4, result.getOptions().size());
        assertEquals("B", result.getOptions().get(1).getLabel());
    }

    @Test
    void saveGeneratedAssetRejectsAnswerOutsideOptions() {
        String invalid = validJson().replace("\"correctAnswer\":\"B\"", "\"correctAnswer\":\"Z\"");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveGeneratedAsset(7L, "test-model", invalid));

        assertTrue(exception.getMessage().contains("正确答案必须对应"));
        verify(questionAiAssetMapper, never()).insert(any());
    }

    @Test
    void submitAnswerGradesAndCompletesFirstAttempt() {
        QuestionAiAsset asset = new QuestionAiAsset();
        asset.setId(21L);
        asset.setQuestionId(7L);
        AiVariantTraining training = training();
        when(questionAiAssetMapper.selectOne(any())).thenReturn(asset);
        when(aiVariantQuestionMapper.selectOne(any())).thenReturn(structuredQuestion());
        when(aiVariantTrainingMapper.selectOne(any())).thenReturn(training);

        AiVariantTrainingVO result = service.submitAnswer(7L, 9L, "b");

        assertTrue(result.getAnswered());
        assertTrue(result.getCorrect());
        assertEquals("B", result.getUserAnswer());
        assertEquals("B", result.getCorrectAnswer());
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getAnsweredTime());
        verify(aiVariantTrainingMapper).updateById(training);
    }

    @Test
    void submitAnswerKeepsFirstResultOnRepeat() {
        QuestionAiAsset asset = new QuestionAiAsset();
        asset.setId(21L);
        AiVariantTraining training = training();
        training.setStatus("COMPLETED");
        training.setUserAnswer("A");
        training.setIsCorrect(0);
        training.setAnsweredTime(LocalDateTime.now().minusMinutes(2));
        training.setCompletedTime(training.getAnsweredTime());
        when(questionAiAssetMapper.selectOne(any())).thenReturn(asset);
        when(aiVariantQuestionMapper.selectOne(any())).thenReturn(structuredQuestion());
        when(aiVariantTrainingMapper.selectOne(any())).thenReturn(training);

        AiVariantTrainingVO result = service.submitAnswer(7L, 9L, "B");

        assertFalse(result.getCorrect());
        assertEquals("A", result.getUserAnswer());
        verify(aiVariantTrainingMapper, never()).updateById(any());
    }

    private AiVariantTraining training() {
        AiVariantTraining training = new AiVariantTraining();
        training.setId(41L);
        training.setUserId(9L);
        training.setQuestionId(7L);
        training.setAssetId(21L);
        training.setStatus("STARTED");
        training.setStartedTime(LocalDateTime.now().minusMinutes(5));
        return training;
    }

    private AiVariantQuestion structuredQuestion() {
        AiVariantQuestion question = new AiVariantQuestion();
        question.setId(31L);
        question.setAssetId(21L);
        question.setQuestionType("SINGLE_CHOICE");
        question.setQuestionContent("以下哪个描述正确？");
        question.setOptionsJson("[{\"label\":\"A\",\"content\":\"选项一\"},{\"label\":\"B\",\"content\":\"选项二\"},{\"label\":\"C\",\"content\":\"选项三\"},{\"label\":\"D\",\"content\":\"选项四\"}]");
        question.setCorrectAnswer("B");
        question.setAnalysis("B 对应核心概念。");
        question.setDifficulty(3);
        return question;
    }

    private String validJson() {
        return """
                {"questionType":"SINGLE_CHOICE","questionContent":"以下哪个描述正确？",
                "options":[{"label":"A","content":"选项一"},{"label":"B","content":"选项二"},
                {"label":"C","content":"选项三"},{"label":"D","content":"选项四"}],
                "correctAnswer":"B","analysis":"B 对应核心概念。","difficulty":3}
                """;
    }
}
