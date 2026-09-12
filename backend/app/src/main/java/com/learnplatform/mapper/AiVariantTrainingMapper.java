package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.AiVariantTraining;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiVariantTrainingMapper extends BaseMapper<AiVariantTraining> {

    @Insert("""
            INSERT INTO ai_variant_training
                (user_id, question_id, asset_id, status, started_time, last_view_time, create_time, update_time)
            VALUES
                (#{userId}, #{questionId}, #{assetId}, 'STARTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                last_view_time = CURRENT_TIMESTAMP,
                update_time = CURRENT_TIMESTAMP
            """)
    int upsertStarted(@Param("userId") Long userId,
                      @Param("questionId") Long questionId,
                      @Param("assetId") Long assetId);
}
