package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.QuestionOption;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 题目选项 Mapper
 */
public interface QuestionOptionMapper extends BaseMapper<QuestionOption> {
    @Select("SELECT * FROM question_option WHERE question_id=#{questionId} AND deleted=0 "
            + "ORDER BY sort_order FOR SHARE")
    List<QuestionOption> selectActiveByQuestionIdForShare(@Param("questionId") Long questionId);
}
