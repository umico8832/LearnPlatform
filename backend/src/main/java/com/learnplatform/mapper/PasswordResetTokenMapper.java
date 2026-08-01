package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.PasswordResetToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PasswordResetTokenMapper extends BaseMapper<PasswordResetToken> {
}
