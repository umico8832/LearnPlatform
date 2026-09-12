package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT id, ai_daily_quota FROM `user` WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    User lockAiQuotaUser(Long id);

}