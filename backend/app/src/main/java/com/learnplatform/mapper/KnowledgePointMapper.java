package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.KnowledgePoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgePointMapper extends BaseMapper<KnowledgePoint> {

    @Select("SELECT * FROM knowledge_point WHERE id = #{knowledgePointId} AND deleted = 0 FOR SHARE")
    KnowledgePoint selectActiveForShare(@Param("knowledgePointId") Long knowledgePointId);

    @Select("SELECT * FROM knowledge_point WHERE id = #{knowledgePointId} AND deleted = 0 FOR UPDATE")
    KnowledgePoint selectActiveForUpdate(@Param("knowledgePointId") Long knowledgePointId);

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
            <script>
            SELECT k.* FROM knowledge_point k JOIN (
              SELECT course_id,
                     MIN(CONCAT(LPAD(sort_order, 10, '0'), ':', LPAD(id, 20, '0'))) first_key
              FROM knowledge_point WHERE deleted=0 AND parent_id=0 AND course_id IN
              <foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
              GROUP BY course_id
            ) roots ON roots.course_id=k.course_id
              AND CONCAT(LPAD(k.sort_order, 10, '0'), ':', LPAD(k.id, 20, '0'))=roots.first_key
            </script>
            """)
    List<KnowledgePoint> selectFirstRoots(@Param("courseIds") List<Long> courseIds);
}
