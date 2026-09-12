package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.WrongQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 错题本 Mapper
 */
@Mapper
public interface WrongQuestionMapper extends BaseMapper<WrongQuestion> {
    @Update("""
            UPDATE wrong_question
            SET wrong_count = COALESCE(wrong_count, 0) + 1,
                mastery_level = CASE
                    WHEN deleted = 1 OR mastery_level = 2 THEN 0
                    ELSE mastery_level
                END,
                last_wrong_answer = #{userAnswer},
                deleted = 0,
                update_time = NOW()
            WHERE user_id = #{userId}
              AND question_id = #{questionId}
            """)
    int reviveOrIncrement(@Param("userId") Long userId,
                          @Param("questionId") Long questionId,
                          @Param("userAnswer") String userAnswer);
}
