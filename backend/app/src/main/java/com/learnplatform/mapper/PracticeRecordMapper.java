package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
            <script>
            SELECT p.* FROM practice_record p
            JOIN question q ON q.id=p.question_id AND q.deleted=0
              AND (q.visibility='PUBLIC' OR (q.visibility='PRIVATE' AND q.owner_user_id=#{userId}))
            WHERE p.user_id=#{userId} AND q.content LIKE #{keyword} ESCAPE '!'
            <if test="courseId != null">AND q.course_id=#{courseId}</if>
            <if test="questionType != null and questionType != ''">AND q.question_type=#{questionType}</if>
            <if test="isCorrect != null">AND p.is_correct=#{isCorrect}</if>
            ORDER BY p.create_time DESC,p.id DESC
            </script>
            """)
    Page<PracticeRecord> selectKeywordPage(Page<PracticeRecord> page, @Param("userId") Long userId,
                                          @Param("questionType") String questionType,
                                          @Param("courseId") Long courseId, @Param("isCorrect") Integer isCorrect,
                                          @Param("keyword") String keyword);

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
