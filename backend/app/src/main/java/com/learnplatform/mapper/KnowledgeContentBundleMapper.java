package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.KnowledgeContentBundle;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeContentBundleMapper extends BaseMapper<KnowledgeContentBundle> {
    @Insert("""
            INSERT INTO knowledge_content_bundle
                (course_key, bundle_version, manifest_hash, source_revision, source_quality_status,
                 review_status, chunk_count, imported_by)
            VALUES (#{courseKey}, #{bundleVersion}, #{manifestHash}, #{sourceRevision}, #{sourceQualityStatus},
                    'PENDING', #{chunkCount}, #{importedBy})
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int reserve(KnowledgeContentBundle bundle);

    @Select("""
            SELECT * FROM knowledge_content_bundle
            WHERE course_key = #{courseKey} AND review_status = 'REVIEWED'
            ORDER BY reviewed_at DESC, id DESC LIMIT 1
            """)
    KnowledgeContentBundle latestReviewed(@Param("courseKey") String courseKey);

    @Select("SELECT * FROM knowledge_content_bundle WHERE id = #{id} FOR UPDATE")
    KnowledgeContentBundle lockById(@Param("id") Long id);
}
