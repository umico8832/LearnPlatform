package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.common.query.ListQuery;
import com.learnplatform.dto.FavoriteQuestionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.UserFavoriteQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.UserFavoriteQuestionMapper;
import com.learnplatform.service.question.QuestionAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        wrapper.notInSql(UserFavoriteQuestion::getQuestionId, QuestionAccessPolicy.DRAFT_IDS_SQL);
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
    @Transactional(readOnly = true)
    public Page<FavoriteQuestionVO> getFavorites(Long userId, int pageNum, int pageSize) {
        // 1. 查询收藏记录
        LambdaQueryWrapper<UserFavoriteQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.notInSql(UserFavoriteQuestion::getQuestionId, QuestionAccessPolicy.DRAFT_IDS_SQL);
        wrapper.eq(UserFavoriteQuestion::getUserId, userId)
               .orderByDesc(UserFavoriteQuestion::getCreateTime);
        Page<UserFavoriteQuestion> favoritePage = favoriteMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        List<FavoriteQuestionVO> voList = favoriteViews(userId, favoritePage.getRecords());

        Page<FavoriteQuestionVO> resultPage = new Page<>(pageNum, pageSize, favoritePage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Transactional(readOnly = true)
    public Page<FavoriteQuestionVO> getFavorites(Long userId, Page<?> page, Long courseId,
                                                  String questionType, String keyword) {
        Page<UserFavoriteQuestion> result = favoriteMapper.selectFilteredPage(
                new Page<>(page.getCurrent(), page.getSize()), userId, courseId, questionType,
                ListQuery.likePattern(keyword));
        Page<FavoriteQuestionVO> views = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        views.setRecords(favoriteViews(userId, result.getRecords()));
        return views;
    }

    private List<FavoriteQuestionVO> favoriteViews(Long userId, List<UserFavoriteQuestion> favorites) {
        if (favorites.isEmpty()) { return List.of(); }
        Map<Long, Question> questions = questionMapper.selectBatchIds(
                favorites.stream().map(UserFavoriteQuestion::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(Question::getId, question -> question));
        List<Long> courseIds = questions.values().stream().map(Question::getCourseId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Course> courses = courseIds.isEmpty() ? Map.of() : courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, course -> course));
        return favorites.stream().map(favorite -> {
            Question question = questions.get(favorite.getQuestionId());
            if (!QuestionAccessPolicy.canAccess(question, userId)) { return null; }
            FavoriteQuestionVO view = new FavoriteQuestionVO();
            view.setId(favorite.getId());
            view.setQuestionId(favorite.getQuestionId());
            view.setQuestionContent(question.getContent());
            view.setQuestionType(question.getQuestionType());
            view.setCourseId(question.getCourseId());
            view.setDifficulty(question.getDifficulty());
            view.setScore(question.getScore());
            view.setCreateTime(favorite.getCreateTime());
            Course course = courses.get(question.getCourseId());
            if (course != null) { view.setCourseName(course.getName()); }
            return view;
        }).filter(Objects::nonNull).toList();
    }

    /**
     * 获取用户收藏题目 ID 列表（用于前端批量判断）
     */
    public List<Long> getFavoriteQuestionIds(Long userId) {
        LambdaQueryWrapper<UserFavoriteQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.notInSql(UserFavoriteQuestion::getQuestionId, QuestionAccessPolicy.DRAFT_IDS_SQL);
        wrapper.eq(UserFavoriteQuestion::getUserId, userId)
               .select(UserFavoriteQuestion::getQuestionId);
        return favoriteMapper.selectList(wrapper).stream()
                .map(UserFavoriteQuestion::getQuestionId)
                .collect(Collectors.toList());
    }
}
