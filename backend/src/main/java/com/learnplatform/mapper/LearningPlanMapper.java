package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.LearningPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习计划 Mapper
 */
@Mapper
public interface LearningPlanMapper extends BaseMapper<LearningPlan> {
}