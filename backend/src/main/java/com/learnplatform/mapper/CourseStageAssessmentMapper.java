package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.CourseStageAssessment;
import com.learnplatform.entity.Question;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CourseStageAssessmentMapper extends BaseMapper<CourseStageAssessment> {
    @Select("SELECT id FROM user_course WHERE user_id = #{userId} AND course_id = #{courseId} FOR UPDATE")
    Long lockUserCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Select("""
            SELECT * FROM course_stage_assessment
            WHERE user_id = #{userId} AND course_id = #{courseId} AND active_session_key = 'ACTIVE'
            LIMIT 1
            """)
    CourseStageAssessment selectActive(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Select("""
            SELECT * FROM course_stage_assessment
            WHERE id = #{assessmentId} AND user_id = #{userId}
            FOR UPDATE
            """)
    CourseStageAssessment selectOwnedForUpdate(@Param("assessmentId") Long assessmentId,
                                                @Param("userId") Long userId);

    @Update("""
            UPDATE course_stage_assessment
            SET status = 'COMPLETED', correct_count = #{correctCount},
                active_session_key = NULL, complete_time = #{completeTime}
            WHERE id = #{assessmentId} AND user_id = #{userId} AND status = 'IN_PROGRESS'
            """)
    int complete(@Param("assessmentId") Long assessmentId,
                 @Param("userId") Long userId,
                 @Param("correctCount") int correctCount,
                 @Param("completeTime") java.time.LocalDateTime completeTime);

    @Select("""
            SELECT q.* FROM question q
            WHERE q.course_id = #{courseId} AND q.status = 1 AND q.deleted = 0
              AND q.question_type IN ('SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE')
              AND (q.visibility = 'PUBLIC' OR (q.visibility = 'PRIVATE' AND q.owner_user_id = #{userId}))
            ORDER BY
              CASE
                WHEN EXISTS (SELECT 1 FROM wrong_question w WHERE w.user_id = #{userId}
                  AND w.question_id = q.id AND w.mastery_level != 2 AND w.deleted = 0) THEN 0
                WHEN EXISTS (SELECT 1 FROM question_review_schedule r WHERE r.user_id = #{userId}
                  AND r.question_id = q.id AND r.next_review_date <= CURRENT_DATE) THEN 1
                WHEN EXISTS (SELECT 1 FROM course_learning_event e WHERE e.user_id = #{userId}
                  AND e.course_id = #{courseId} AND e.subject_type = 'QUESTION'
                  AND e.subject_id = q.id AND e.payload_json = '{"isCorrect":false}') THEN 2
                ELSE 3
              END,
              q.id
            LIMIT #{limit}
            """)
    List<Question> selectCandidateQuestions(@Param("userId") Long userId,
                                            @Param("courseId") Long courseId,
                                            @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM question q
            WHERE q.course_id = #{courseId} AND q.status = 1 AND q.deleted = 0
              AND q.question_type IN ('SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE')
              AND (q.visibility = 'PUBLIC' OR (q.visibility = 'PRIVATE' AND q.owner_user_id = #{userId}))
              AND (
                EXISTS (SELECT 1 FROM wrong_question w WHERE w.user_id = #{userId}
                  AND w.question_id = q.id AND w.mastery_level != 2 AND w.deleted = 0)
                OR EXISTS (SELECT 1 FROM question_review_schedule r WHERE r.user_id = #{userId}
                  AND r.question_id = q.id AND r.next_review_date <= CURRENT_DATE)
                OR EXISTS (SELECT 1 FROM course_learning_event e WHERE e.user_id = #{userId}
                  AND e.course_id = #{courseId} AND e.subject_type = 'QUESTION'
                  AND e.subject_id = q.id AND e.payload_json = '{"isCorrect":false}')
              )
            """)
    Long countPrioritySignals(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
