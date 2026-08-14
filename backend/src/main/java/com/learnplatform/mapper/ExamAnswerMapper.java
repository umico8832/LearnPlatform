package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.ExamAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExamAnswerMapper extends BaseMapper<ExamAnswer> {
    @Select("SELECT * FROM exam_answer WHERE id = #{id} FOR UPDATE")
    ExamAnswer selectByIdForUpdate(@Param("id") Long id);
}
