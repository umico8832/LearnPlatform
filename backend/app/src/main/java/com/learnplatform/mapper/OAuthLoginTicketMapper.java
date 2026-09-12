package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.OAuthLoginTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OAuthLoginTicketMapper extends BaseMapper<OAuthLoginTicket> {
    @Update("""
            UPDATE oauth_login_ticket
            SET used_at = #{usedAt}
            WHERE id = #{id} AND used_at IS NULL AND expires_at > #{usedAt}
            """)
    int consume(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt);
}
