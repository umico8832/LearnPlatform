package com.learnplatform.dto;

import com.learnplatform.entity.QuestionOption;

/**
 * 题目选项 VO
 */
public class QuestionOptionVO {
    private Long id;
    private String content;
    private String optionLabel;
    private Integer isCorrect;
    private Integer sortOrder;

    public static QuestionOptionVO fromEntity(QuestionOption o) {
        QuestionOptionVO vo = new QuestionOptionVO();
        vo.setId(o.getId());
        vo.setContent(o.getContent());
        vo.setOptionLabel(o.getOptionLabel());
        vo.setIsCorrect(o.getIsCorrect());
        vo.setSortOrder(o.getSortOrder());
        return vo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getOptionLabel() { return optionLabel; }
    public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }
    public Integer getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Integer isCorrect) { this.isCorrect = isCorrect; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}