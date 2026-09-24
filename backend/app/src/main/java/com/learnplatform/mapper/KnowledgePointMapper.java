package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.KnowledgePoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgePointMapper extends BaseMapper<KnowledgePoint> {

    @Select("""
            SELECT kp.* FROM knowledge_point kp
            JOIN question_knowledge_point qkp ON qkp.knowledge_point_id = kp.id
            WHERE qkp.question_id = #{questionId} AND kp.deleted = 0
            ORDER BY kp.id
            """)
    List<KnowledgePoint> selectByQuestionId(@Param("questionId") Long questionId);

    @Select("""
            SELECT question_id FROM question_knowledge_point
            WHERE knowledge_point_id = #{knowledgePointId}
            """)
    List<Long> selectQuestionIdsByKnowledgePointId(@Param("knowledgePointId") Long knowledgePointId);

    @Select("""
            SELECT SUM(reference_count) FROM (
                SELECT COUNT(*) AS reference_count
                FROM knowledge_point WHERE parent_id = #{knowledgePointId} AND deleted = 0
                UNION ALL SELECT COUNT(*)
                FROM question_knowledge_point WHERE knowledge_point_id = #{knowledgePointId}
                UNION ALL SELECT COUNT(*)
                FROM tutor_content WHERE knowledge_point_id = #{knowledgePointId}
                UNION ALL SELECT COUNT(*)
                FROM tutor_session WHERE knowledge_point_id = #{knowledgePointId}
                UNION ALL SELECT COUNT(*)
                FROM course_stage_assessment WHERE target_knowledge_point_id = #{knowledgePointId}
                UNION ALL SELECT COUNT(*)
                FROM question_submission
                WHERE FIND_IN_SET(
                    CAST(#{knowledgePointId} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci,
                    knowledge_point_ids COLLATE utf8mb4_unicode_ci
                ) > 0
                UNION ALL SELECT COUNT(*) FROM community_post
                WHERE knowledge_point_id = #{knowledgePointId} AND deleted = 0
            ) AS knowledge_point_references
            """)
    Long countReferences(@Param("knowledgePointId") Long knowledgePointId);
}
