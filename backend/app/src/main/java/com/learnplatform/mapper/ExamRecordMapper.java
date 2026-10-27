package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.entity.ExamRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    @Select("""
            <script>
            SELECT er.* FROM exam_record er
            LEFT JOIN exam_paper p ON p.id=er.exam_paper_id AND p.deleted=0
            WHERE er.user_id=#{userId}
            <if test="courseId != null">AND p.course_id=#{courseId}</if>
            <if test="keyword != null">AND p.title LIKE #{keyword} ESCAPE '!'</if>
            <if test="status != null">
              AND (CASE WHEN er.status=0 AND p.duration IS NOT NULL AND er.start_time IS NOT NULL
                    AND TIMESTAMPADD(MINUTE,p.duration,COALESCE(er.start_instant_utc,
                        CONVERT_TZ(er.start_time,'America/New_York','+00:00'))) &lt;= #{nowUtc}
                   THEN 2 ELSE er.status END)=#{status}
            </if>
            ORDER BY er.create_time DESC
            </script>
            """)
    Page<ExamRecord> selectFilteredPage(Page<ExamRecord> page, @Param("userId") Long userId,
                                       @Param("courseId") Long courseId, @Param("keyword") String keyword,
                                       @Param("status") Integer status, @Param("nowUtc") LocalDateTime nowUtc);

    @Select("SELECT * FROM exam_record WHERE id = #{id} FOR UPDATE")
    ExamRecord selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM exam_record WHERE active_exam_key = #{activeExamKey}")
    ExamRecord selectByActiveExamKey(@Param("activeExamKey") String activeExamKey);

    @Select("SELECT * FROM exam_record WHERE active_exam_key = #{activeExamKey} FOR UPDATE")
    ExamRecord selectByActiveExamKeyForUpdate(@Param("activeExamKey") String activeExamKey);
}
