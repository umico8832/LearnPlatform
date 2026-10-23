package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {
    @Select("SELECT * FROM course WHERE id = #{courseId} AND deleted = 0 FOR SHARE")
    Course selectActiveForShare(@Param("courseId") Long courseId);

    @Select("SELECT * FROM course WHERE id = #{courseId} AND deleted = 0 FOR UPDATE")
    Course selectActiveForUpdate(@Param("courseId") Long courseId);

}
