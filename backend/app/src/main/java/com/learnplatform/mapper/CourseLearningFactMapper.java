package com.learnplatform.mapper;

import com.learnplatform.dto.CourseKnowledgePointFactVO;
import com.learnplatform.dto.CourseOverviewVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 只读已有作答、测评快照和当前待办；不维护另一份学习状态。 */
@Mapper
public interface CourseLearningFactMapper {
    CourseOverviewVO selectTotals(@Param("userId") Long userId, @Param("courseId") Long courseId,
                                  @Param("today") LocalDate today);

    long countKnowledgePoints(@Param("userId") Long userId, @Param("courseId") Long courseId,
                              @Param("today") LocalDate today);

    List<CourseKnowledgePointFactVO> selectKnowledgePoints(
            @Param("userId") Long userId, @Param("courseId") Long courseId, @Param("today") LocalDate today,
            @Param("offset") long offset, @Param("pageSize") int pageSize);

    Long selectFirstDueQuestion(@Param("userId") Long userId, @Param("courseId") Long courseId,
                                @Param("today") LocalDate today);

    Long selectFirstWrongQuestion(@Param("userId") Long userId, @Param("courseId") Long courseId);

    List<CourseOverviewVO.TutorProgressVO> selectTutorProgress(
            @Param("userId") Long userId, @Param("courseId") Long courseId);
}
