package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.QuestionVersion;
import org.apache.ibatis.annotations.Mapper;

/** 题目版本记录 Mapper。 */
@Mapper
public interface QuestionVersionMapper extends BaseMapper<QuestionVersion> {
}
