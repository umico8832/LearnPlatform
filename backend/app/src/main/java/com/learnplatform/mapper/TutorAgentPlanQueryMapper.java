package com.learnplatform.mapper;

import com.learnplatform.entity.TutorAgentMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TutorAgentPlanQueryMapper {
    @Select("""
            SELECT COUNT(*) > 0 FROM tutor_agent_run run
            JOIN tutor_session session ON session.id = run.tutor_session_id
            WHERE run.run_key = #{runKey} AND run.user_id = #{userId}
              AND session.user_id = #{userId} AND session.course_id = #{courseId}
              AND session.session_key = #{sessionKey}
            """)
    boolean ownsRun(@Param("userId") Long userId, @Param("courseId") Long courseId,
                    @Param("sessionKey") String sessionKey, @Param("runKey") String runKey);

    @Select("""
            SELECT message.* FROM tutor_agent_message message
            JOIN tutor_agent_run run ON run.id = message.run_id
            JOIN tutor_session session ON session.id = run.tutor_session_id
            WHERE run.run_key = #{runKey} AND run.user_id = #{userId}
              AND session.user_id = #{userId} AND session.course_id = #{courseId}
              AND session.session_key = #{sessionKey} AND message.sequence_no = #{sequence}
              AND message.role = 'ASSISTANT'
            """)
    TutorAgentMessage findPlanMessage(@Param("userId") Long userId, @Param("courseId") Long courseId,
                                      @Param("sessionKey") String sessionKey, @Param("runKey") String runKey,
                                      @Param("sequence") Integer sequence);

    @Select("""
            SELECT message.* FROM tutor_agent_message message
            JOIN tutor_agent_run run ON run.id = message.run_id
            JOIN tutor_session session ON session.id = run.tutor_session_id
            WHERE run.run_key = #{runKey} AND run.user_id = #{userId}
              AND session.user_id = #{userId} AND session.course_id = #{courseId}
              AND session.session_key = #{sessionKey} AND message.sequence_no = #{sequence}
              AND message.role = 'ASSISTANT'
            FOR UPDATE
            """)
    TutorAgentMessage findPlanMessageForUpdate(@Param("userId") Long userId, @Param("courseId") Long courseId,
                                               @Param("sessionKey") String sessionKey, @Param("runKey") String runKey,
                                               @Param("sequence") Integer sequence);

    @Select("""
            SELECT message.* FROM tutor_agent_message message
            JOIN tutor_agent_run run ON run.id = message.run_id
            JOIN tutor_session session ON session.id = run.tutor_session_id
            WHERE run.run_key = #{runKey} AND run.user_id = #{userId}
              AND session.user_id = #{userId} AND session.course_id = #{courseId}
              AND session.session_key = #{sessionKey} AND message.role = 'ASSISTANT'
              AND JSON_CONTAINS(message.actions_json, JSON_OBJECT('type', 'PLAN'))
            ORDER BY message.sequence_no DESC, message.id DESC LIMIT 1
            """)
    TutorAgentMessage findLatestPlanMessage(@Param("userId") Long userId, @Param("courseId") Long courseId,
                                            @Param("sessionKey") String sessionKey, @Param("runKey") String runKey);
}
