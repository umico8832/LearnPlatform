package com.learnplatform.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论展示 VO
 */
public class CommentVO {

    private Long id;
    private Long questionId;
    private Long userId;
    private String nickname;
    private String avatar;
    private String content;
    private Long parentId;
    private Long replyToUserId;
    private String replyToNickname;
    private Integer likeCount;
    private Boolean likedByMe;
    private LocalDateTime createTime;
    private List<CommentVO> replies;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getReplyToUserId() { return replyToUserId; }
    public void setReplyToUserId(Long replyToUserId) { this.replyToUserId = replyToUserId; }
    public String getReplyToNickname() { return replyToNickname; }
    public void setReplyToNickname(String replyToNickname) { this.replyToNickname = replyToNickname; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Boolean getLikedByMe() { return likedByMe; }
    public void setLikedByMe(Boolean likedByMe) { this.likedByMe = likedByMe; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public List<CommentVO> getReplies() { return replies; }
    public void setReplies(List<CommentVO> replies) { this.replies = replies; }
}