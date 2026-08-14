package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.AiVariantQuestionVO;
import com.learnplatform.dto.AiVariantReviewRequest;
import com.learnplatform.dto.AiVariantReviewVO;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.entity.AiVariantQuestion;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionAiAsset;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.mapper.AiVariantQuestionMapper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionAiAssetMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 管理员审查结构化 AI 变式题，并将通过项物化为正式可追溯题目。 */
@Service
public class AiVariantReviewService {
    private static final Set<String> REVIEW_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

    private final AiVariantQuestionMapper variantMapper;
    private final QuestionAiAssetMapper assetMapper;
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final QuestionService questionService;
    private final ObjectMapper objectMapper;

    public AiVariantReviewService(AiVariantQuestionMapper variantMapper,
                                  QuestionAiAssetMapper assetMapper,
                                  QuestionMapper questionMapper,
                                  CourseMapper courseMapper,
                                  QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                  QuestionService questionService,
                                  ObjectMapper objectMapper) {
        this.variantMapper = variantMapper;
        this.assetMapper = assetMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.questionService = questionService;
        this.objectMapper = objectMapper;
    }

    public Page<AiVariantReviewVO> list(String reviewStatus, int pageNum, int pageSize) {
        String status = reviewStatus == null || reviewStatus.isBlank() ? "PENDING" : reviewStatus;
        if (!REVIEW_STATUSES.contains(status) || pageNum < 1 || pageSize < 1 || pageSize > 50) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "查询参数不合法");
        }
        Page<AiVariantQuestion> source = variantMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AiVariantQuestion>()
                        .eq(AiVariantQuestion::getReviewStatus, status)
                        .orderByAsc(AiVariantQuestion::getCreateTime));
        Page<AiVariantReviewVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(this::toView).toList());
        return result;
    }

    @Transactional
    public AiVariantReviewVO review(Long variantId, AiVariantReviewRequest request, Long reviewerId) {
        AiVariantQuestion variant = variantMapper.selectForUpdate(variantId);
        if (variant == null) throw notFound();
        if (!"PENDING".equals(variant.getReviewStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该变式题已完成审查");
        }
        QuestionAiAsset asset = assetMapper.selectById(variant.getAssetId());
        Question mother = asset == null ? null : questionMapper.selectById(asset.getQuestionId());
        if (mother == null || !Integer.valueOf(1).equals(mother.getStatus())
                || !"PUBLIC".equals(mother.getVisibility())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "母题不可用于发布 AI 生成题");
        }

        LocalDateTime reviewedTime = LocalDateTime.now();
        variant.setReviewNote(trimToNull(request.getReviewNote()));
        variant.setReviewedBy(reviewerId);
        variant.setReviewedTime(reviewedTime);
        if ("REJECT".equals(request.getDecision())) {
            if (variant.getReviewNote() == null) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "驳回时必须填写审查说明");
            }
            variant.setReviewStatus("REJECTED");
            variantMapper.updateById(variant);
            return toView(variant);
        }

        QuestionCreateRequest create = buildCreateRequest(variant, mother);
        QuestionVO published = questionService.createReviewedAiQuestion(
                create, reviewerId, "AI_VARIANT:" + variant.getId(), mother.getId());
        variant.setReviewStatus("APPROVED");
        variant.setPublishedQuestionId(published.getId());
        variantMapper.updateById(variant);
        return toView(variant);
    }

    private QuestionCreateRequest buildCreateRequest(AiVariantQuestion variant, Question mother) {
        List<AiVariantQuestionVO.Option> options = readOptions(variant.getOptionsJson());
        if (options.stream().noneMatch(option -> variant.getCorrectAnswer().equals(option.getLabel()))) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "变式题答案与选项不一致");
        }
        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setContent(variant.getQuestionContent());
        request.setQuestionType(variant.getQuestionType());
        request.setCourseId(mother.getCourseId());
        request.setDifficulty(variant.getDifficulty());
        request.setAnalysis(variant.getAnalysis());
        request.setScore(1);
        request.setOptions(java.util.stream.IntStream.range(0, options.size()).mapToObj(index -> {
            AiVariantQuestionVO.Option source = options.get(index);
            QuestionCreateRequest.OptionItem item = new QuestionCreateRequest.OptionItem();
            item.setOptionLabel(source.getLabel());
            item.setContent(source.getContent());
            item.setIsCorrect(variant.getCorrectAnswer().equals(source.getLabel()) ? 1 : 0);
            item.setSortOrder(index + 1);
            return item;
        }).toList());
        request.setKnowledgePointIds(questionKnowledgePointMapper.selectList(
                new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .eq(QuestionKnowledgePoint::getQuestionId, mother.getId())).stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId).toList());
        return request;
    }

    private AiVariantReviewVO toView(AiVariantQuestion variant) {
        QuestionAiAsset asset = assetMapper.selectById(variant.getAssetId());
        Question mother = asset == null ? null : questionMapper.selectById(asset.getQuestionId());
        if (mother == null) throw notFound();
        Course course = courseMapper.selectById(mother.getCourseId());
        AiVariantReviewVO view = new AiVariantReviewVO();
        view.setId(variant.getId());
        view.setMotherQuestionId(mother.getId());
        view.setMotherQuestionContent(mother.getContent());
        view.setCourseId(mother.getCourseId());
        view.setCourseName(course == null ? null : course.getName());
        view.setQuestionContent(variant.getQuestionContent());
        view.setQuestionType(variant.getQuestionType());
        view.setOptions(readOptions(variant.getOptionsJson()));
        view.setCorrectAnswer(variant.getCorrectAnswer());
        view.setAnalysis(variant.getAnalysis());
        view.setDifficulty(variant.getDifficulty());
        view.setReviewStatus(variant.getReviewStatus());
        view.setReviewNote(variant.getReviewNote());
        view.setReviewedBy(variant.getReviewedBy());
        view.setReviewedTime(variant.getReviewedTime());
        view.setPublishedQuestionId(variant.getPublishedQuestionId());
        return view;
    }

    private List<AiVariantQuestionVO.Option> readOptions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "变式题选项数据损坏");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException notFound() {
        return new BusinessException(ResultCode.NOT_FOUND, "AI 变式题不存在");
    }
}
