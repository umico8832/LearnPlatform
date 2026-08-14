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
}
