package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {
    @Select("""
            SELECT SUM(reference_count) FROM (
                SELECT COUNT(*) AS reference_count FROM knowledge_point WHERE course_id = #{courseId} AND deleted = 0
                UNION ALL SELECT COUNT(*) FROM question WHERE course_id = #{courseId} AND deleted = 0
                UNION ALL SELECT COUNT(*) FROM exam_paper WHERE course_id = #{courseId} AND deleted = 0
                UNION ALL SELECT COUNT(*) FROM user_course WHERE course_id = #{courseId}
                UNION ALL SELECT COUNT(*) FROM course_learning_event WHERE course_id = #{courseId}
                UNION ALL SELECT COUNT(*) FROM tutor_session WHERE course_id = #{courseId}
                UNION ALL SELECT COUNT(*) FROM course_stage_assessment WHERE course_id = #{courseId}
                UNION ALL SELECT COUNT(*) FROM question_submission WHERE course_id = #{courseId}
                UNION ALL SELECT COUNT(*) FROM community_post WHERE course_id = #{courseId} AND deleted = 0
            ) AS course_references
            """)
    Long countReferences(@Param("courseId") Long courseId);
}
