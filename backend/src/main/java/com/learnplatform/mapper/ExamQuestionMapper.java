package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.ExamQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamQuestionMapper extends BaseMapper<ExamQuestion> {
}