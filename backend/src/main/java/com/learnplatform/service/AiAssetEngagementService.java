package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.AiVariantTrainingVO;
import com.learnplatform.entity.AiVariantTraining;
import com.learnplatform.entity.QuestionAiAsset;
import com.learnplatform.mapper.AiAssetViewMapper;
import com.learnplatform.mapper.AiVariantTrainingMapper;
import com.learnplatform.mapper.QuestionAiAssetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AI 学习资产交互服务：记录资产查看并管理当前缓存版本的变式训练。
 */
@Service
public class AiAssetEngagementService {

    private final AiAssetViewMapper aiAssetViewMapper;
    private final QuestionAiAssetMapper questionAiAssetMapper;
    private final AiVariantTrainingMapper aiVariantTrainingMapper;
    private final AiVariantQuestionService aiVariantQuestionService;

    public AiAssetEngagementService(AiAssetViewMapper aiAssetViewMapper,
                                    QuestionAiAssetMapper questionAiAssetMapper,
                                    AiVariantTrainingMapper aiVariantTrainingMapper,
                                    AiVariantQuestionService aiVariantQuestionService) {
        this.aiAssetViewMapper = aiAssetViewMapper;
        this.questionAiAssetMapper = questionAiAssetMapper;
        this.aiVariantTrainingMapper = aiVariantTrainingMapper;
        this.aiVariantQuestionService = aiVariantQuestionService;
    }

    /**
     * 记录用户确实看到某类已缓存学习资产。数据库按天原子聚合重复查看。
     */
    @Transactional
    public AiVariantTrainingVO recordAssetView(Long questionId, AiAssetType assetType, Long userId) {
        QuestionAiAsset asset = questionAiAssetMapper.selectOne(new LambdaQueryWrapper<QuestionAiAsset>()
                .eq(QuestionAiAsset::getQuestionId, questionId)
                .eq(QuestionAiAsset::getAssetType, assetType.name()));
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学习资产不存在");
        }
        aiAssetViewMapper.upsertDailyView(userId, questionId, assetType.name());
        if (assetType != AiAssetType.VARIANT) {
            return null;
        }
        aiVariantTrainingMapper.upsertStarted(userId, questionId, asset.getId());
        return toVariantTrainingVO(findVariantTraining(userId, asset.getId()));
    }

    /**
     * 用户显式确认完成当前缓存版本的变式训练。重复确认保持幂等。
     */
    @Transactional
    public AiVariantTrainingVO completeVariantTraining(Long questionId, Long userId) {
        QuestionAiAsset asset = questionAiAssetMapper.selectOne(new LambdaQueryWrapper<QuestionAiAsset>()
                .eq(QuestionAiAsset::getQuestionId, questionId)
                .eq(QuestionAiAsset::getAssetType, AiAssetType.VARIANT.name()));
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "变式题学习资产不存在");
        }
        if (aiVariantQuestionService.hasStructuredQuestion(asset.getId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "结构化变式题请提交答案完成训练");
        }
        AiVariantTraining training = findVariantTraining(userId, asset.getId());
        if (training == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "请先查看变式题，再标记训练完成");
        }
        if (!"COMPLETED".equals(training.getStatus())) {
            training.setStatus("COMPLETED");
            training.setCompletedTime(LocalDateTime.now());
            aiVariantTrainingMapper.updateById(training);
        }
        return toVariantTrainingVO(training);
    }

    public AiVariantTrainingVO submitVariantAnswer(Long questionId, Long userId, String userAnswer) {
        return aiVariantQuestionService.submitAnswer(questionId, userId, userAnswer);
    }

    private AiVariantTraining findVariantTraining(Long userId, Long assetId) {
        return aiVariantTrainingMapper.selectOne(new LambdaQueryWrapper<AiVariantTraining>()
                .eq(AiVariantTraining::getUserId, userId)
                .eq(AiVariantTraining::getAssetId, assetId));
    }

    private AiVariantTrainingVO toVariantTrainingVO(AiVariantTraining training) {
        if (training == null) { return null; }
        AiVariantTrainingVO vo = new AiVariantTrainingVO();
        vo.setQuestionId(training.getQuestionId());
        vo.setAssetId(training.getAssetId());
        vo.setStatus(training.getStatus());
        vo.setCompleted("COMPLETED".equals(training.getStatus()));
        vo.setStartedTime(training.getStartedTime());
        vo.setCompletedTime(training.getCompletedTime());
        AiVariantTrainingVO enriched = aiVariantQuestionService.enrichTrainingVO(training, vo);
        return enriched != null ? enriched : vo;
    }
}
