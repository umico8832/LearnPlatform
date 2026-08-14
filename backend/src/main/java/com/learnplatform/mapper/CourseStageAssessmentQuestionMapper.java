package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.CourseStageAssessmentQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CourseStageAssessmentQuestionMapper extends BaseMapper<CourseStageAssessmentQuestion> {
    @Select("""
            SELECT * FROM course_stage_assessment_question
            WHERE assessment_id = #{assessmentId}
            ORDER BY sort_order
            """)
    List<CourseStageAssessmentQuestion> selectByAssessmentId(@Param("assessmentId") Long assessmentId);

    @Select("""
            <script>
            SELECT assessment_id, source_category_snapshot
            FROM course_stage_assessment_question
            WHERE assessment_id IN
            <foreach collection="assessmentIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<CourseStageAssessmentQuestion> selectSourcesByAssessmentIds(
            @Param("assessmentIds") List<Long> assessmentIds);
}
