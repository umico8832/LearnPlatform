package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.QuestionReviewSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 间隔重复复习计划 Mapper
 */
@Mapper
public interface QuestionReviewScheduleMapper extends BaseMapper<QuestionReviewSchedule> {
    @Insert("""
            INSERT INTO question_review_schedule
                (user_id, question_id, ease_factor, interval_days, repetitions,
                 next_review_date, total_reviews, deleted)
            VALUES
                (#{userId}, #{questionId}, #{easeFactor}, 0, 0, #{nextReviewDate}, 0, 0)
            ON DUPLICATE KEY UPDATE deleted = 0
            """)
    int insertOrRestore(@Param("userId") Long userId,
                        @Param("questionId") Long questionId,
                        @Param("easeFactor") BigDecimal easeFactor,
                        @Param("nextReviewDate") LocalDate nextReviewDate);

    @Select("SELECT * FROM question_review_schedule WHERE user_id = #{userId} "
            + "AND question_id = #{questionId} AND deleted = 0 FOR UPDATE")
    QuestionReviewSchedule selectByUserAndQuestionForUpdate(@Param("userId") Long userId,
                                                            @Param("questionId") Long questionId);
}
