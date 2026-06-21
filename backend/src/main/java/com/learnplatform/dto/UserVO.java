package com.learnplatform.dto;

import com.learnplatform.entity.User;

/**
 * 用户视图对象（不包含密码等敏感信息）
 */
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private Integer status;
    private Integer aiDailyQuota;
    private String createTime;

    public UserVO() {
    }

    public static UserVO fromUser(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setAiDailyQuota(user.getAiDailyQuota());
        if (user.getCreateTime() != null) {
            vo.setCreateTime(user.getCreateTime().toString());
        }
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getAiDailyQuota() {
        return aiDailyQuota;
    }

    public void setAiDailyQuota(Integer aiDailyQuota) {
        this.aiDailyQuota = aiDailyQuota;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}
