package com.learnplatform.dto;

import com.learnplatform.entity.Course;

public class CourseVO {
    private Long id;
    private String name;
    private String description;
    private String coverImage;
    private String contentKey;
    private String contentSource;
    private Integer sortOrder;
    private Integer status;
    private String createTime;

    public static CourseVO fromEntity(Course c) {
        CourseVO vo = new CourseVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setDescription(c.getDescription());
        vo.setCoverImage(c.getCoverImage());
        vo.setContentKey(c.getContentKey());
        vo.setContentSource(c.getContentSource());
        vo.setSortOrder(c.getSortOrder());
        vo.setStatus(c.getStatus());
        if (c.getCreateTime() != null) vo.setCreateTime(c.getCreateTime().toString());
        return vo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public String getContentKey() { return contentKey; }
    public void setContentKey(String contentKey) { this.contentKey = contentKey; }
    public String getContentSource() { return contentSource; }
    public void setContentSource(String contentSource) { this.contentSource = contentSource; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
