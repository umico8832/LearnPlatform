package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.WrongQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 错题本 Mapper
 */
@Mapper
public interface WrongQuestionMapper extends BaseMapper<WrongQuestion> {
}