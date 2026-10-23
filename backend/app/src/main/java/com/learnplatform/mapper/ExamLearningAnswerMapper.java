package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.ExamLearningAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExamLearningAnswerMapper extends BaseMapper<ExamLearningAnswer> {
    @Select("""
            SELECT attempt_no FROM exam_learning_answer
            WHERE session_id=#{sessionId} AND question_id=#{questionId}
            ORDER BY attempt_no DESC LIMIT 1 FOR SHARE
            """)
    Integer selectLatestAttemptNoForShare(@Param("sessionId") Long sessionId, @Param("questionId") Long questionId);

}
