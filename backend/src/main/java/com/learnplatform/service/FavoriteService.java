package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.FavoriteQuestionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.UserFavoriteQuestion;
import com.learnplatform.mapper.*;
import com.learnplatform.service.question.QuestionAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目收藏服务
 */
@Service
public class FavoriteService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteService.class);

    private final UserFavoriteQuestionMapper favoriteMapper;
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;

    public FavoriteService(UserFavoriteQuestionMapper favoriteMapper,
                           QuestionMapper questionMapper,
                           CourseMapper courseMapper) {
        this.favoriteMapper = favoriteMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 收藏题目
     */
    public void addFavorite(Long userId, Long questionId) {
        // 检查题目是否存在
        Question question = questionMapper.selectById(questionId);
        if (!QuestionAccessPolicy.canAccess(question, userId)
                || Integer.valueOf(1).equals(question.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
        // 检查是否已收藏
        int count = favoriteMapper.countByUserAndQuestion(userId, questionId);
        if (count > 0) {
            throw new BusinessException("已收藏该题目");
        }
        UserFavoriteQuestion favorite = new UserFavoriteQuestion();
        favorite.setUserId(userId);
        favorite.setQuestionId(questionId);
        favoriteMapper.insert(favorite);
        log.info("用户 {} 收藏题目 {}", userId, questionId);
    }

    /**
     * 取消收藏
     */
    public void removeFavorite(Long userId, Long questionId) {
        LambdaQueryWrapper<UserFavoriteQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavoriteQuestion::getUserId, userId)
               .eq(UserFavoriteQuestion::getQuestionId, questionId);
        int deleted = favoriteMapper.delete(wrapper);
        if (deleted == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收藏记录不存在");
        }
        log.info("用户 {} 取消收藏题目 {}", userId, questionId);
    }

    /**
     * 检查是否已收藏
     */
    public boolean isFavorite(Long userId, Long questionId) {
        return favoriteMapper.countByUserAndQuestion(userId, questionId) > 0;
    }

    /**
     * 获取收藏列表（分页）
     */
    public Page<FavoriteQuestionVO> getFavorites(Long userId, int pageNum, int pageSize) {
        // 1. 查询收藏记录
        LambdaQueryWrapper<UserFavoriteQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavoriteQuestion::getUserId, userId)
               .orderByDesc(UserFavoriteQuestion::getCreateTime);
        Page<UserFavoriteQuestion> favoritePage = favoriteMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        // 2. 组装 VO
        List<FavoriteQuestionVO> voList = favoritePage.getRecords().stream()
                .map(fav -> {
                    Question question = questionMapper.selectById(fav.getQuestionId());
                    if (!QuestionAccessPolicy.canAccess(question, userId)) { return null; }
                    FavoriteQuestionVO vo = new FavoriteQuestionVO();
                    vo.setId(fav.getId());
                    vo.setQuestionId(fav.getQuestionId());
                    vo.setQuestionContent(question.getContent());
                    vo.setQuestionType(question.getQuestionType());
                    vo.setCourseId(question.getCourseId());
                    vo.setDifficulty(question.getDifficulty());
                    vo.setScore(question.getScore());
                    vo.setCreateTime(fav.getCreateTime());
                    // 查询课程名称
                    Course course = courseMapper.selectById(question.getCourseId());
                    if (course != null) {
                        vo.setCourseName(course.getName());
                    }
                    return vo;
                })
                .filter(vo -> vo != null)
                .collect(Collectors.toList());

        Page<FavoriteQuestionVO> resultPage = new Page<>(pageNum, pageSize, favoritePage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    /**
     * 获取用户收藏题目 ID 列表（用于前端批量判断）
     */
    public List<Long> getFavoriteQuestionIds(Long userId) {
        LambdaQueryWrapper<UserFavoriteQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavoriteQuestion::getUserId, userId)
               .select(UserFavoriteQuestion::getQuestionId);
        return favoriteMapper.selectList(wrapper).stream()
                .map(UserFavoriteQuestion::getQuestionId)
                .collect(Collectors.toList());
    }
}
