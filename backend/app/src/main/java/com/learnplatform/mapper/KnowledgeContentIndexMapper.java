package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.KnowledgeContentIndex;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface KnowledgeContentIndexMapper extends BaseMapper<KnowledgeContentIndex> {
    @Insert("""
            INSERT INTO knowledge_content_index
                (bundle_id,index_key,model,dimensions,collection_name,vector_endpoint_hash,status)
            VALUES (#{bundleId},#{indexKey},#{model},#{dimensions},#{collectionName},#{vectorEndpointHash},'FAILED')
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int reserve(KnowledgeContentIndex index);

    @Select("SELECT * FROM knowledge_content_index WHERE id=#{id} FOR UPDATE")
    KnowledgeContentIndex lockById(@Param("id") Long id);

    @Update("""
            UPDATE knowledge_content_index index_entry
            JOIN knowledge_content_bundle bundle ON bundle.id=index_entry.bundle_id
            SET index_entry.lease_until=#{leaseUntil}, index_entry.indexed_count=#{indexedCount}
            WHERE index_entry.id=#{id} AND index_entry.run_key=#{runKey} AND index_entry.status='INDEXING'
              AND bundle.review_status='REVIEWED' AND #{indexedCount} >= index_entry.indexed_count
              AND #{indexedCount} <= bundle.chunk_count
            """)
    int renew(@Param("id") Long id, @Param("runKey") String runKey,
              @Param("indexedCount") int indexedCount, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE knowledge_content_index index_entry
            JOIN knowledge_content_bundle bundle ON bundle.id=index_entry.bundle_id
            SET index_entry.status='READY', index_entry.lease_until=NULL, index_entry.indexed_count=#{indexedCount}
            WHERE index_entry.id=#{id} AND index_entry.run_key=#{runKey} AND index_entry.status='INDEXING'
              AND bundle.review_status='REVIEWED' AND #{indexedCount}=bundle.chunk_count
            """)
    int complete(@Param("id") Long id, @Param("runKey") String runKey, @Param("indexedCount") int indexedCount);

    @Update("""
            UPDATE knowledge_content_index index_entry
            JOIN knowledge_content_bundle bundle ON bundle.id=index_entry.bundle_id
            SET index_entry.status='FAILED', index_entry.lease_until=NULL
            WHERE index_entry.id=#{id} AND index_entry.run_key=#{runKey} AND index_entry.status='INDEXING'
              AND bundle.review_status='REVIEWED'
            """)
    int fail(@Param("id") Long id, @Param("runKey") String runKey);

    @Select("""
            SELECT index_entry.* FROM knowledge_content_index index_entry
            JOIN knowledge_content_bundle bundle ON bundle.id=index_entry.bundle_id
            WHERE index_entry.bundle_id=#{bundleId} AND index_entry.index_key=#{indexKey}
              AND index_entry.status='READY' AND bundle.review_status='REVIEWED'
            """)
    KnowledgeContentIndex ready(@Param("bundleId") Long bundleId, @Param("indexKey") String indexKey);

    @Select("SELECT * FROM knowledge_content_index WHERE bundle_id=#{bundleId} AND status <> 'PURGED'")
    List<KnowledgeContentIndex> listNonPurgedByBundle(@Param("bundleId") Long bundleId);

    @Update("UPDATE knowledge_content_index SET status='DELETED' "
            + "WHERE bundle_id=#{bundleId} AND status <> 'PURGED'")
    int markDeletedByBundle(@Param("bundleId") Long bundleId);

    @Update("UPDATE knowledge_content_index SET status='PURGED' WHERE id=#{id} AND status='DELETED' "
            + "AND (lease_until IS NULL OR lease_until <= NOW())")
    int purged(@Param("id") Long id);
}
