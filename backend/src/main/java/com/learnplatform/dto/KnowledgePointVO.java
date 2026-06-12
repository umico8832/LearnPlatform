package com.learnplatform.dto;

import com.learnplatform.entity.KnowledgePoint;
import java.util.List;

public class KnowledgePointVO {
    private Long id;
    private String name;
    private String description;
    private Long courseId;
    private Long parentId;
    private Integer sortOrder;
    private String createTime;
    private List<KnowledgePointVO> children;

    public static KnowledgePointVO fromEntity(KnowledgePoint kp) {
        KnowledgePointVO vo = new KnowledgePointVO();
        vo.setId(kp.getId());
        vo.setName(kp.getName());
        vo.setDescription(kp.getDescription());
        vo.setCourseId(kp.getCourseId());
        vo.setParentId(kp.getParentId());
        vo.setSortOrder(kp.getSortOrder());
        if (kp.getCreateTime() != null) vo.setCreateTime(kp.getCreateTime().toString());
        return vo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public List<KnowledgePointVO> getChildren() { return children; }
    public void setChildren(List<KnowledgePointVO> children) { this.children = children; }
}