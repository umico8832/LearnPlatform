package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.PracticeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 刷题记录 Mapper
 */
@Mapper
public interface PracticeRecordMapper extends BaseMapper<PracticeRecord> {
    @Select("""
            SELECT COUNT(*) AS totalAnswered,
                   COALESCE(SUM(CASE WHEN p.is_correct=1 THEN 1 ELSE 0 END), 0) AS correctCount
            FROM practice_record p
            WHERE p.user_id=#{userId}
              AND p.question_id NOT IN (SELECT id FROM question WHERE visibility='DRAFT')
            """)
    PracticeStats aggregateVisibleStats(@Param("userId") Long userId);

    record PracticeStats(long totalAnswered, long correctCount) { }
}
