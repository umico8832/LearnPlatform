package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.CommentRequest;
import com.learnplatform.dto.CommentVO;
import com.learnplatform.entity.CommentLike;
import com.learnplatform.entity.QuestionComment;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.CommentLikeMapper;
import com.learnplatform.mapper.QuestionCommentMapper;
import com.learnplatform.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目评论/讨论服务
 */
@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final QuestionCommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final UserMapper userMapper;

    public CommentService(QuestionCommentMapper commentMapper,
                          CommentLikeMapper commentLikeMapper,
                          UserMapper userMapper) {
        this.commentMapper = commentMapper;
        this.commentLikeMapper = commentLikeMapper;
        this.userMapper = userMapper;
    }

    /**
     * 获取题目评论列表（树形：顶级评论 + 回复）
     */
    public List<CommentVO> getComments(Long questionId, Long currentUserId) {
        // 查询该题所有有效评论
        List<QuestionComment> all = commentMapper.selectList(
                new LambdaQueryWrapper<QuestionComment>()
                        .eq(QuestionComment::getQuestionId, questionId)
                        .eq(QuestionComment::getStatus, 1)
                        .orderByAsc(QuestionComment::getCreateTime)
        );
        if (all.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集所有涉及的 userId
        Set<Long> userIds = new HashSet<>();
        for (QuestionComment c : all) {
            userIds.add(c.getUserId());
            if (c.getReplyToUserId() != null) {
                userIds.add(c.getReplyToUserId());
            }
        }
        Map<Long, User> userMap = batchGetUsers(userIds);

        // 查询当前用户已点赞的评论ID
        Set<Long> likedCommentIds = new HashSet<>();
        if (currentUserId != null) {
            List<CommentLike> likes = commentLikeMapper.selectList(
                    new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getUserId, currentUserId)
                            .in(CommentLike::getCommentId, all.stream().map(QuestionComment::getId).collect(Collectors.toList()))
            );
            likedCommentIds = likes.stream().map(CommentLike::getCommentId).collect(Collectors.toSet());
        }

        // 构建 VO Map
        Map<Long, CommentVO> voMap = new LinkedHashMap<>();
        for (QuestionComment c : all) {
            CommentVO vo = toVO(c, userMap, likedCommentIds);
            voMap.put(c.getId(), vo);
        }

        // 组装树形结构
        List<CommentVO> roots = new ArrayList<>();
        for (QuestionComment c : all) {
            CommentVO vo = voMap.get(c.getId());
            if (c.getParentId() == null || c.getParentId() == 0) {
                roots.add(vo);
            } else {
                CommentVO parent = voMap.get(c.getParentId());
                if (parent != null) {
                    if (parent.getReplies() == null) {
                        parent.setReplies(new ArrayList<>());
                    }
                    parent.getReplies().add(vo);
                } else {
                    // 父评论不存在（可能已删除），作为顶级展示
                    roots.add(vo);
                }
            }
        }

        // 最新评论在前
        Collections.reverse(roots);
        return roots;
    }

    /**
     * 发表评论
     */
    @Transactional
    public CommentVO addComment(CommentRequest request, Long userId) {
        QuestionComment comment = new QuestionComment();
        comment.setQuestionId(request.getQuestionId());
        comment.setUserId(userId);
        comment.setContent(request.getContent());
        comment.setParentId(request.getParentId() != null ? request.getParentId() : 0L);
        comment.setReplyToUserId(request.getReplyToUserId());
        comment.setLikeCount(0);
        comment.setStatus(1);
        commentMapper.insert(comment);

        log.info("用户{}发表题目{}评论，评论ID={}", userId, request.getQuestionId(), comment.getId());

        User user = userMapper.selectById(userId);
        Set<Long> userIds = new HashSet<>();
        userIds.add(userId);
        if (request.getReplyToUserId() != null) {
            userIds.add(request.getReplyToUserId());
        }
        Map<Long, User> userMap = batchGetUsers(userIds);

        return toVO(comment, userMap, Collections.emptySet());
    }

    /**
     * 删除评论（仅限本人）
     */
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        QuestionComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的评论");
        }
        commentMapper.deleteById(commentId);

        // 同时删除子评论
        List<QuestionComment> children = commentMapper.selectList(
                new LambdaQueryWrapper<QuestionComment>()
                        .eq(QuestionComment::getParentId, commentId)
        );
        for (QuestionComment child : children) {
            commentMapper.deleteById(child.getId());
        }

        log.info("用户{}删除评论{}", userId, commentId);
    }

    /**
     * 点赞/取消点赞
     */
    @Transactional
    public boolean toggleLike(Long commentId, Long userId) {
        QuestionComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }

        CommentLike existing = commentLikeMapper.selectOne(
                new LambdaQueryWrapper<CommentLike>()
                        .eq(CommentLike::getCommentId, commentId)
                        .eq(CommentLike::getUserId, userId)
        );

        if (existing != null) {
            // 取消点赞
            commentLikeMapper.deleteById(existing.getId());
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            commentMapper.updateById(comment);
            return false;
        } else {
            // 点赞
            CommentLike like = new CommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            commentLikeMapper.insert(like);
            comment.setLikeCount(comment.getLikeCount() + 1);
            commentMapper.updateById(comment);
            return true;
        }
    }

    /**
     * 获取题目评论数
     */
    public int getCommentCount(Long questionId) {
        return Math.toIntExact(commentMapper.selectCount(
                new LambdaQueryWrapper<QuestionComment>()
                        .eq(QuestionComment::getQuestionId, questionId)
                        .eq(QuestionComment::getStatus, 1)
        ));
    }

    // ===== 私有方法 =====

    private CommentVO toVO(QuestionComment c, Map<Long, User> userMap, Set<Long> likedCommentIds) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setQuestionId(c.getQuestionId());
        vo.setUserId(c.getUserId());
        vo.setContent(c.getContent());
        vo.setParentId(c.getParentId());
        vo.setReplyToUserId(c.getReplyToUserId());
        vo.setLikeCount(c.getLikeCount());
        vo.setLikedByMe(likedCommentIds.contains(c.getId()));
        vo.setCreateTime(c.getCreateTime());

        User user = userMap.get(c.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname() != null ? user.getNickname() : user.getUsername());
            vo.setAvatar(user.getAvatar());
        }
        if (c.getReplyToUserId() != null) {
            User replyTo = userMap.get(c.getReplyToUserId());
            if (replyTo != null) {
                vo.setReplyToNickname(replyTo.getNickname() != null ? replyTo.getNickname() : replyTo.getUsername());
            }
        }

        return vo;
    }

    private Map<Long, User> batchGetUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> map = new HashMap<>();
        for (User u : users) {
            map.put(u.getId(), u);
        }
        return map;
    }
}