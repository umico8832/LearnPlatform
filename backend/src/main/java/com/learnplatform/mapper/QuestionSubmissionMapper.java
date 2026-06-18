package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.QuestionSubmission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目投稿 Mapper
 */
@Mapper
public interface QuestionSubmissionMapper extends BaseMapper<QuestionSubmission> {
}