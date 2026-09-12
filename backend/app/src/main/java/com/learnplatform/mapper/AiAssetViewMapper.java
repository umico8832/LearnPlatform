package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.AiAssetView;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI 学习资产查看记录 Mapper。
 */
@Mapper
public interface AiAssetViewMapper extends BaseMapper<AiAssetView> {

    @Insert("""
            INSERT INTO ai_asset_view
                (user_id, question_id, asset_type, view_date, view_count,
                 first_view_time, last_view_time, create_time, update_time)
            VALUES
                (#{userId}, #{questionId}, #{assetType}, CURRENT_DATE, 1,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                view_count = view_count + 1,
                last_view_time = CURRENT_TIMESTAMP,
                update_time = CURRENT_TIMESTAMP
            """)
    int upsertDailyView(@Param("userId") Long userId,
                        @Param("questionId") Long questionId,
                        @Param("assetType") String assetType);
}
