package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
}
