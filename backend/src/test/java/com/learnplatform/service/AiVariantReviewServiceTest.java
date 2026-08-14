package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.AiVariantReviewRequest;
import com.learnplatform.dto.AiVariantReviewVO;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.entity.AiVariantQuestion;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionAiAsset;
import com.learnplatform.mapper.AiVariantQuestionMapper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionAiAssetMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiVariantReviewServiceTest {
    @Mock private AiVariantQuestionMapper variantMapper;
    @Mock private QuestionAiAssetMapper assetMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private QuestionService questionService;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private AiVariantReviewService service;

    @BeforeEach
    void setUp() {
        service = new AiVariantReviewService(variantMapper, assetMapper, questionMapper,
                courseMapper, questionKnowledgePointMapper, questionService, new ObjectMapper());
    }

    @Test
    void approvesValidatedVariantByPublishingFormalQuestionWithMotherReference() {
        AiVariantQuestion variant = variant();
        when(variantMapper.selectForUpdate(12L)).thenReturn(variant);
        when(assetMapper.selectById(22L)).thenReturn(asset());
        when(questionMapper.selectById(21L)).thenReturn(motherQuestion());
        when(courseMapper.selectById(10L)).thenReturn(course());
        QuestionVO published = new QuestionVO();
        published.setId(81L);
        when(questionService.createReviewedAiQuestion(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq("AI_VARIANT:12"),
                org.mockito.ArgumentMatchers.eq(21L))).thenReturn(published);
        AiVariantReviewRequest request = new AiVariantReviewRequest();
        request.setDecision("APPROVE");
        request.setReviewNote("结构与答案核验通过");

        AiVariantReviewVO result = service.review(12L, request, 3L);

        assertEquals("APPROVED", result.getReviewStatus());
        assertEquals(81L, result.getPublishedQuestionId());
        ArgumentCaptor<QuestionCreateRequest> createCaptor = ArgumentCaptor.forClass(QuestionCreateRequest.class);
        verify(questionService).createReviewedAiQuestion(createCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq("AI_VARIANT:12"),
                org.mockito.ArgumentMatchers.eq(21L));
        assertEquals(10L, createCaptor.getValue().getCourseId());
        assertEquals(1, createCaptor.getValue().getOptions().get(0).getIsCorrect());
    }

    @Test
    void rejectsPrivateMotherAndNeverPublishesVariant() {
        when(variantMapper.selectForUpdate(12L)).thenReturn(variant());
        when(assetMapper.selectById(22L)).thenReturn(asset());
        Question mother = motherQuestion();
        mother.setVisibility("PRIVATE");
        when(questionMapper.selectById(21L)).thenReturn(mother);
        AiVariantReviewRequest request = new AiVariantReviewRequest();
        request.setDecision("APPROVE");

        assertThrows(BusinessException.class, () -> service.review(12L, request, 3L));
    }

    @Test
    void requiresReviewNoteWhenRejectingVariant() {
        when(variantMapper.selectForUpdate(12L)).thenReturn(variant());
        when(assetMapper.selectById(22L)).thenReturn(asset());
        when(questionMapper.selectById(21L)).thenReturn(motherQuestion());
        AiVariantReviewRequest request = new AiVariantReviewRequest();
        request.setDecision("REJECT");

        assertThrows(BusinessException.class, () -> service.review(12L, request, 3L));
    }

    private AiVariantQuestion variant() {
        AiVariantQuestion value = new AiVariantQuestion();
        value.setId(12L);
        value.setAssetId(22L);
        value.setQuestionType("SINGLE_CHOICE");
        value.setQuestionContent("下列关于栈的说法正确的是？");
        value.setOptionsJson("[{\"label\":\"A\",\"content\":\"后进先出\"},{\"label\":\"B\",\"content\":\"先进先出\"}]");
        value.setCorrectAnswer("A");
        value.setAnalysis("栈遵循后进先出。");
        value.setDifficulty(2);
        value.setReviewStatus("PENDING");
        return value;
    }

    private QuestionAiAsset asset() {
        QuestionAiAsset asset = new QuestionAiAsset();
        asset.setId(22L);
        asset.setQuestionId(21L);
        asset.setAssetType("VARIANT");
        return asset;
    }

    private Question motherQuestion() {
        Question question = new Question();
        question.setId(21L);
        question.setCourseId(10L);
        question.setStatus(1);
        question.setVisibility("PUBLIC");
        question.setContent("栈的特点是什么？");
        return question;
    }

    private Course course() {
        Course course = new Course();
        course.setId(10L);
        course.setName("408 数据结构");
        return course;
    }
}
