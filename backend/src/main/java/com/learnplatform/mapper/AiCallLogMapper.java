package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 调用日志 Mapper
 */
@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
}