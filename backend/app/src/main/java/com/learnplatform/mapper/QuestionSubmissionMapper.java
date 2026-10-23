package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.QuestionSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 题目投稿 Mapper
 */
@Mapper
public interface QuestionSubmissionMapper extends BaseMapper<QuestionSubmission> {
    @Select("SELECT * FROM question_submission WHERE id=#{id} AND deleted=0 FOR UPDATE")
    QuestionSubmission selectActiveForUpdate(@Param("id") Long id);
}
