package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.Question;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 题目 Mapper
 */
public interface QuestionMapper extends BaseMapper<Question> {
    @Select("SELECT * FROM question WHERE id=#{id} AND deleted=0 FOR SHARE")
    Question selectActiveForShare(@Param("id") Long id);

    @Select("SELECT * FROM question WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Question selectActiveForUpdate(@Param("id") Long id);
}
