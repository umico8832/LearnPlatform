package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.UserFavoriteQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户收藏题目 Mapper
 */
@Mapper
public interface UserFavoriteQuestionMapper extends BaseMapper<UserFavoriteQuestion> {

    /**
     * 查询用户是否已收藏某题目
     */
    @Select("SELECT COUNT(*) FROM user_favorite_question WHERE user_id = #{userId} AND question_id = #{questionId}")
    int countByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);
}