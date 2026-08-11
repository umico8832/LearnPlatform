package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.ExamLearningSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExamLearningSessionMapper extends BaseMapper<ExamLearningSession> {
    @Select("SELECT * FROM exam_learning_session WHERE id = #{id} FOR UPDATE")
    ExamLearningSession selectByIdForUpdate(@Param("id") Long id);
}
