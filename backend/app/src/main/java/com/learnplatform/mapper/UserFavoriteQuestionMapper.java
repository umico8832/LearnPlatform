package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.entity.UserFavoriteQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户收藏题目 Mapper
 */
@Mapper
public interface UserFavoriteQuestionMapper extends BaseMapper<UserFavoriteQuestion> {

    @Select("""
            <script>
            SELECT f.* FROM user_favorite_question f
            JOIN question q ON q.id=f.question_id AND q.deleted=0
              AND (q.visibility IS NULL OR q.visibility='PUBLIC'
                   OR (q.visibility='PRIVATE' AND q.owner_user_id=#{userId}))
            WHERE f.user_id=#{userId}
            <if test="courseId != null">AND q.course_id=#{courseId}</if>
            <if test="questionType != null and questionType != ''">AND q.question_type=#{questionType}</if>
            <if test="keyword != null">AND q.content LIKE #{keyword} ESCAPE '!'</if>
            ORDER BY f.create_time DESC,f.id DESC
            </script>
            """)
    Page<UserFavoriteQuestion> selectFilteredPage(Page<UserFavoriteQuestion> page, @Param("userId") Long userId,
                                                 @Param("courseId") Long courseId,
                                                 @Param("questionType") String questionType,
                                                 @Param("keyword") String keyword);

    /**
     * 查询用户是否已收藏某题目
     */
    @Select("SELECT COUNT(*) FROM user_favorite_question WHERE user_id = #{userId} AND question_id = #{questionId}")
    int countByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);
}