package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.QuestionReviewSchedule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 间隔重复复习计划 Mapper
 */
@Mapper
public interface QuestionReviewScheduleMapper extends BaseMapper<QuestionReviewSchedule> {
}