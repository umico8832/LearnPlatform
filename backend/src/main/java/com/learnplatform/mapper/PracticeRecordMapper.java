package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.PracticeRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 刷题记录 Mapper
 */
@Mapper
public interface PracticeRecordMapper extends BaseMapper<PracticeRecord> {
}