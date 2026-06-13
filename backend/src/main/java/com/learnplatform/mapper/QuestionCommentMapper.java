package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.QuestionComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionCommentMapper extends BaseMapper<QuestionComment> {
}