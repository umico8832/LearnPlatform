package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.entity.AiVariantTraining;
import com.learnplatform.entity.QuestionAiAsset;
import com.learnplatform.mapper.AiVariantTrainingMapper;
import com.learnplatform.mapper.QuestionAiAssetMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实 MySQL 下验证变式训练唯一约束、幂等开始和显式完成。 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
@Tag("integration")
class AiVariantTrainingIntegrationTest extends IntegrationTestBase {

    @Autowired private AiLearningEffectService learningEffectService;
    @Autowired private QuestionAiAssetMapper questionAiAssetMapper;
    @Autowired private AiVariantTrainingMapper aiVariantTrainingMapper;

    @Test
    void variantTrainingIsIdempotentForOneUserAndAssetVersion() {
        QuestionAiAsset asset = new QuestionAiAsset();
        asset.setQuestionId(990001L);
        asset.setAssetType(AiAssetType.VARIANT.name());
        asset.setContent("## 变式题\n测试内容");
        asset.setModel("integration-test");
        asset.setDeleted(0);
        questionAiAssetMapper.insert(asset);

        var firstStart = learningEffectService.recordAssetView(990001L, AiAssetType.VARIANT, 880001L);
        var secondStart = learningEffectService.recordAssetView(990001L, AiAssetType.VARIANT, 880001L);

        assertEquals("STARTED", firstStart.getStatus());
        assertEquals(firstStart.getAssetId(), secondStart.getAssetId());
        assertEquals(1L, aiVariantTrainingMapper.selectCount(new LambdaQueryWrapper<AiVariantTraining>()
                .eq(AiVariantTraining::getUserId, 880001L)
                .eq(AiVariantTraining::getAssetId, asset.getId())));

        var completed = learningEffectService.completeVariantTraining(990001L, 880001L);
        var repeated = learningEffectService.completeVariantTraining(990001L, 880001L);

        assertTrue(completed.getCompleted());
        assertEquals("COMPLETED", repeated.getStatus());
        assertNotNull(repeated.getCompletedTime());

        var effect = learningEffectService.getLearningEffect(30);
        assertEquals(1L, effect.getVariantTrainingStartedCount());
        assertEquals(1L, effect.getVariantTrainingCompletedCount());
        assertEquals(100.0, effect.getVariantTrainingCompletionRate());
    }
}
