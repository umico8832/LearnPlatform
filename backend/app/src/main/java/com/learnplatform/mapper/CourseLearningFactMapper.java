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

    List<CourseOverviewVO> selectTotalsByCourses(
            @Param("userId") Long userId,
            @Param("courseIds") List<Long> courseIds,
            @Param("today") LocalDate today);

    List<CourseQuestion> selectFirstDueQuestions(
            @Param("userId") Long userId,
            @Param("courseIds") List<Long> courseIds,
            @Param("today") LocalDate today);

    List<CourseQuestion> selectFirstWrongQuestions(
            @Param("userId") Long userId,
            @Param("courseIds") List<Long> courseIds);

    List<CourseTutorProgress> selectTutorProgressByCourses(
            @Param("userId") Long userId,
            @Param("courseIds") List<Long> courseIds);

    class CourseQuestion {
        private Long courseId;
        private Long questionId;

        public Long getCourseId() {
            return courseId;
        }

        public void setCourseId(Long value) {
            courseId = value;
        }

        public Long getQuestionId() {
            return questionId;
        }

        public void setQuestionId(Long value) {
            questionId = value;
        }
    }

    class CourseTutorProgress extends CourseOverviewVO.TutorProgressVO {
        private Long courseId;

        public Long getCourseId() {
            return courseId;
        }

        public void setCourseId(Long value) {
            courseId = value;
        }
    }
}
