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
}