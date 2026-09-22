package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.KnowledgeContentChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeContentChunkMapper extends BaseMapper<KnowledgeContentChunk> {
    @Select("SELECT * FROM knowledge_content_chunk WHERE bundle_id = #{bundleId} ORDER BY id")
    List<KnowledgeContentChunk> listForBundle(@Param("bundleId") Long bundleId);

    @Select("SELECT COUNT(*) FROM knowledge_content_chunk WHERE bundle_id = #{bundleId}")
    int countForBundle(@Param("bundleId") Long bundleId);
}
