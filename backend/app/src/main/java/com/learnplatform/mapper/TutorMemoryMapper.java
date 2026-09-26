package com.learnplatform.mapper;

import com.learnplatform.dto.TutorMemoryVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TutorMemoryMapper {
    @Select("""
            SELECT uc.id FROM user_course uc JOIN course c ON c.id = uc.course_id
            WHERE uc.user_id = #{userId} AND uc.course_id = #{courseId} AND c.status = 1 AND c.deleted = 0
            """)
    Long member(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Select("""
            SELECT uc.id FROM user_course uc JOIN course c ON c.id = uc.course_id
            WHERE uc.user_id = #{userId} AND uc.course_id = #{courseId} AND c.status = 1 AND c.deleted = 0
            FOR UPDATE
            """)
    Long lockMember(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Select("""
            SELECT revision, explanation_style, goal FROM tutor_course_memory
            WHERE user_id = #{userId} AND course_id = #{courseId}
            """)
    TutorMemoryVO find(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Insert("""
            INSERT INTO tutor_course_memory (user_id, course_id, revision, explanation_style, goal)
            VALUES (#{userId}, #{courseId}, 1, #{style}, #{goal})
            """)
    void insert(@Param("userId") Long userId, @Param("courseId") Long courseId,
                @Param("style") String style, @Param("goal") String goal);

    @Update("""
            UPDATE tutor_course_memory SET revision = revision + 1, explanation_style = #{style}, goal = #{goal}
            WHERE user_id = #{userId} AND course_id = #{courseId} AND revision = #{revision}
            """)
    int update(@Param("userId") Long userId, @Param("courseId") Long courseId,
               @Param("revision") long revision, @Param("style") String style, @Param("goal") String goal);
}
