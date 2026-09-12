package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.PrivateExamImportDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PrivateExamImportDraftMapper extends BaseMapper<PrivateExamImportDraft> {
    @Select("SELECT * FROM private_exam_import_draft WHERE id = #{id} AND owner_user_id = #{ownerUserId} FOR UPDATE")
    PrivateExamImportDraft selectOwnedForUpdate(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId);
}
