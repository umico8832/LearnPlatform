package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.QuestionAiAsset;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 题目学习资产 Mapper
 */
@Mapper
public interface QuestionAiAssetMapper extends BaseMapper<QuestionAiAsset> {
}