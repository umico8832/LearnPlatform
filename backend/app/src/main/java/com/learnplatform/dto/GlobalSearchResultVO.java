package com.learnplatform.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 全局搜索结果 VO
 * Phase 18：全局搜索与快捷导航
 */
public class GlobalSearchResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<SearchItem> questions;
    private List<SearchItem> courses;
    private List<SearchItem> knowledgePoints;
    private int totalCount;

    public GlobalSearchResultVO() {
    }

    public GlobalSearchResultVO(List<SearchItem> questions, List<SearchItem> courses,
                                List<SearchItem> knowledgePoints) {
        this.questions = questions;
        this.courses = courses;
        this.knowledgePoints = knowledgePoints;
        this.totalCount = (questions != null ? questions.size() : 0)
                + (courses != null ? courses.size() : 0)
                + (knowledgePoints != null ? knowledgePoints.size() : 0);
    }

    public List<SearchItem> getQuestions() {
        return questions;
    }

    public void setQuestions(List<SearchItem> questions) {
        this.questions = questions;
    }

    public List<SearchItem> getCourses() {
        return courses;
    }

    public void setCourses(List<SearchItem> courses) {
        this.courses = courses;
    }

    public List<SearchItem> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<SearchItem> knowledgePoints) {
        this.knowledgePoints = knowledgePoints;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    /**
     * 单条搜索结果项
     */
    public static class SearchItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String title;
        private String subtitle;
        private String type;       // QUESTION / COURSE / KNOWLEDGE_POINT
        private String link;       // 前端跳转路径
        private String highlight;  // 高亮片段（可选）

        public SearchItem() {
        }

        public SearchItem(Long id, String title, String subtitle, String type, String link) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.type = type;
            this.link = link;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public void setSubtitle(String subtitle) {
            this.subtitle = subtitle;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getLink() {
            return link;
        }

        public void setLink(String link) {
            this.link = link;
        }

        public String getHighlight() {
            return highlight;
        }

        public void setHighlight(String highlight) {
            this.highlight = highlight;
        }
    }
}