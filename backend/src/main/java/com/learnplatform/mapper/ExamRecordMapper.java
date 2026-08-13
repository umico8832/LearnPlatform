package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.ExamRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    @Select("SELECT * FROM exam_record WHERE id = #{id} FOR UPDATE")
    ExamRecord selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM exam_record WHERE active_exam_key = #{activeExamKey}")
    ExamRecord selectByActiveExamKey(@Param("activeExamKey") String activeExamKey);

    @Select("SELECT * FROM exam_record WHERE active_exam_key = #{activeExamKey} FOR UPDATE")
    ExamRecord selectByActiveExamKeyForUpdate(@Param("activeExamKey") String activeExamKey);
}
