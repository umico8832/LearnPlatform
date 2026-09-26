package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.TutorAgentPracticeAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TutorAgentPracticeAttemptMapper extends BaseMapper<TutorAgentPracticeAttempt> {
    @Select("SELECT * FROM tutor_agent_practice_attempt WHERE message_id=#{messageId}")
    TutorAgentPracticeAttempt selectByMessageId(Long messageId);

    @Select("SELECT * FROM tutor_agent_practice_attempt WHERE message_id=#{messageId} FOR UPDATE")
    TutorAgentPracticeAttempt selectForUpdate(Long messageId);
}
