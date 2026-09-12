package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.QuestionReviewRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目复审记录 Mapper
 */
@Mapper
public interface QuestionReviewRecordMapper extends BaseMapper<QuestionReviewRecord> {
}