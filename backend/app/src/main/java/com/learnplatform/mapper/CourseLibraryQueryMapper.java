package com.learnplatform.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.UserCourseVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/** 在数据库中对学习者可见课程库分页。 */
@Mapper
public interface CourseLibraryQueryMapper {
    Page<UserCourseVO> selectPage(Page<UserCourseVO> page,
                                  @Param("userId") Long userId,
                                  @Param("courseId") Long courseId,
                                  @Param("keyword") String keyword,
                                  @Param("learningStatus") String learningStatus,
                                  @Param("today") LocalDate today,
                                  @Param("sortBy") String sortBy);
}
