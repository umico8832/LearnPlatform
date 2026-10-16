package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
    @Select("""
            SELECT COUNT(*) FROM ai_call_log l WHERE l.user_id=#{userId} AND l.create_time>=#{start}
            AND (l.function_type<>'question_ask' OR l.run_id IS NULL OR NOT EXISTS (
                SELECT 1 FROM ai_call_log first_call WHERE first_call.user_id=l.user_id
                AND first_call.function_type='question_ask' AND first_call.run_id=l.run_id AND first_call.id<l.id))
            """)
    long countQuota(@Param("userId") Long userId, @Param("start") LocalDateTime start);
}
