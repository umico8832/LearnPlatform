package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.TutorAgentRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TutorAgentRunMapper extends BaseMapper<TutorAgentRun> {
    @Update("""
            UPDATE tutor_agent_run
            SET status='RUNNING', execution_key=#{executionKey},
                lease_until=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE)
            WHERE id=#{id} AND (status IN ('WAITING_USER', 'FAILED')
                OR (status='RUNNING' AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)))
            """)
    int claim(@Param("id") Long id, @Param("executionKey") String executionKey);

    @Update("""
            UPDATE tutor_agent_run
            SET status='WAITING_USER', next_sequence=next_sequence+2, execution_key=NULL, lease_until=NULL
            WHERE id=#{id} AND status='RUNNING' AND execution_key=#{executionKey}
                AND next_sequence=#{sequence} AND lease_until > CURRENT_TIMESTAMP
            """)
    int complete(@Param("id") Long id, @Param("executionKey") String executionKey,
                 @Param("sequence") int sequence);

    @Update("""
            UPDATE tutor_agent_run SET status='FAILED', execution_key=NULL, lease_until=NULL
            WHERE id=#{id} AND status='RUNNING' AND execution_key=#{executionKey}
            """)
    int fail(@Param("id") Long id, @Param("executionKey") String executionKey);

    @Select("""
            SELECT COUNT(*) FROM tutor_agent_run
            WHERE id=#{id} AND status='RUNNING' AND lease_until > CURRENT_TIMESTAMP
            """)
    boolean hasActiveLease(@Param("id") Long id);
}
