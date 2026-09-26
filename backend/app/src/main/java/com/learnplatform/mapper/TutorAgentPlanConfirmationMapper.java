package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.TutorAgentPlanConfirmation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TutorAgentPlanConfirmationMapper extends BaseMapper<TutorAgentPlanConfirmation> {
    @Select("SELECT * FROM tutor_agent_plan_confirmation WHERE message_id=#{messageId}")
    TutorAgentPlanConfirmation selectByMessageId(Long messageId);

    @Select("SELECT * FROM tutor_agent_plan_confirmation WHERE message_id=#{messageId} FOR UPDATE")
    TutorAgentPlanConfirmation selectForUpdate(Long messageId);
}
