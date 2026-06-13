package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.ExamQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExamQuestionMapper extends BaseMapper<ExamQuestion> {

    @Select("""
            SELECT COUNT(*)
            FROM exam_question eq
            JOIN exam_paper ep ON ep.id = eq.exam_paper_id
            WHERE eq.question_id = #{questionId}
              AND ep.status = 1
              AND ep.deleted = 0
            """)
    long countPublishedPapersByQuestionId(@Param("questionId") Long questionId);
}
