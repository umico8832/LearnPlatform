package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.PrivateExamSourceStorageItemVO;
import com.learnplatform.entity.UserExamSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserExamSourceMapper extends BaseMapper<UserExamSource> {
    @Select("SELECT * FROM user_exam_source WHERE id = #{sourceId} AND owner_user_id = #{ownerUserId}")
    UserExamSource selectOwnedWithFile(@Param("sourceId") Long sourceId,
                                       @Param("ownerUserId") Long ownerUserId);

    @Select("SELECT id FROM user WHERE id = #{ownerUserId} AND deleted = 0 FOR UPDATE")
    Long lockOwner(@Param("ownerUserId") Long ownerUserId);

    @Select("SELECT COALESCE(SUM(source_size), 0) FROM user_exam_source WHERE owner_user_id = #{ownerUserId}")
    Long sumOwnedFileSize(@Param("ownerUserId") Long ownerUserId);

    @Select("SELECT COUNT(*) FROM user_exam_source WHERE owner_user_id = #{ownerUserId} AND source_size IS NOT NULL")
    Long countOwnedFiles(@Param("ownerUserId") Long ownerUserId);

    @Select("""
            SELECT s.id, s.source_name, s.source_format, s.source_media_type, s.source_size, s.create_time,
                   CASE WHEN p.id IS NOT NULL THEN 'PAPER'
                        WHEN d.id IS NOT NULL THEN 'DRAFT' ELSE 'UNREFERENCED' END AS association_type,
                   COALESCE(p.id, d.id) AS association_id,
                   COALESCE(p.title, d.title) AS association_title,
                   CASE WHEN p.id IS NOT NULL THEN 'CONFIRMED'
                        ELSE COALESCE(d.status, 'UNREFERENCED') END AS association_status
            FROM user_exam_source s
            LEFT JOIN exam_paper p ON p.source_record_id = s.id
                AND p.owner_user_id = s.owner_user_id AND p.visibility = 'PRIVATE' AND p.deleted = 0
            LEFT JOIN private_exam_import_draft d ON d.source_record_id = s.id
                AND d.owner_user_id = s.owner_user_id AND d.confirmed_paper_id IS NULL
            WHERE s.owner_user_id = #{ownerUserId} AND s.source_size IS NOT NULL
            ORDER BY s.create_time DESC, s.id DESC
            """)
    Page<PrivateExamSourceStorageItemVO> selectOwnedStoredFiles(
            Page<PrivateExamSourceStorageItemVO> page,
            @Param("ownerUserId") Long ownerUserId);
}
