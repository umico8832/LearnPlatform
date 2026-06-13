package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.ExamPaper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExamPaperMapper extends BaseMapper<ExamPaper> {

    @Select("SELECT * FROM exam_paper WHERE id = #{id} FOR UPDATE")
    ExamPaper selectByIdForUpdate(@Param("id") Long id);
}
