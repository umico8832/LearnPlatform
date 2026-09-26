package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT id, ai_daily_quota FROM `user` WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    User lockAiQuotaUser(Long id);

    @Update("""
            UPDATE `user` SET password = #{password}, auth_version = COALESCE(auth_version, 0) + 1,
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = 0
            """)
    int resetPasswordAndRevokeTokens(@Param("id") Long id, @Param("password") String password);

}
