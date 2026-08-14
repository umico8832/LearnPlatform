package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.AiVariantQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiVariantQuestionMapper extends BaseMapper<AiVariantQuestion> {
    @Select("SELECT * FROM ai_variant_question WHERE id = #{id} FOR UPDATE")
    AiVariantQuestion selectForUpdate(Long id);
}
