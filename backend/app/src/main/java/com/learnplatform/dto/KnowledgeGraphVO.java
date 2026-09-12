package com.learnplatform.dto;

import java.util.List;

/**
 * 知识图谱数据 VO
 */
public class KnowledgeGraphVO {

    /** 图谱节点列表 */
    private List<GraphNode> nodes;

    /** 图谱边列表（父子关系） */
    private List<GraphEdge> edges;

    /** 课程信息 */
    private List<CourseInfo> courses;

    public List<GraphNode> getNodes() { return nodes; }
    public void setNodes(List<GraphNode> nodes) { this.nodes = nodes; }
    public List<GraphEdge> getEdges() { return edges; }
    public void setEdges(List<GraphEdge> edges) { this.edges = edges; }
    public List<CourseInfo> getCourses() { return courses; }
    public void setCourses(List<CourseInfo> courses) { this.courses = courses; }

    /**
     * 图谱节点
     */
    public static class GraphNode {
        private Long id;
        private String name;
        private Long courseId;
        private String courseName;
        private Long parentId;
        /** 节点类型：root(课程) / parent(父知识点) / leaf(叶子知识点) */
        private String nodeType;
        /** 掌握程度：0-未练习 1-薄弱 2-需复习 3-已掌握 */
        private int masteryLevel;
        /** 正确率百分比 */
        private double accuracy;
        /** 练习次数 */
        private int practiceCount;
        /** 错题数量 */
        private int wrongCount;
        /** 分类（课程名，用于 ECharts 分组） */
        private String category;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public String getNodeType() { return nodeType; }
        public void setNodeType(String nodeType) { this.nodeType = nodeType; }
        public int getMasteryLevel() { return masteryLevel; }
        public void setMasteryLevel(int masteryLevel) { this.masteryLevel = masteryLevel; }
        public double getAccuracy() { return accuracy; }
        public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
        public int getPracticeCount() { return practiceCount; }
        public void setPracticeCount(int practiceCount) { this.practiceCount = practiceCount; }
        public int getWrongCount() { return wrongCount; }
        public void setWrongCount(int wrongCount) { this.wrongCount = wrongCount; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }

    /**
     * 图谱边
     */
    public static class GraphEdge {
        private Long source;
        private Long target;
        /** 关系类型：parent-child / course-topic */
        private String relationType;

        public GraphEdge() {}

        public GraphEdge(Long source, Long target, String relationType) {
            this.source = source;
            this.target = target;
            this.relationType = relationType;
        }

        public Long getSource() { return source; }
        public void setSource(Long source) { this.source = source; }
        public Long getTarget() { return target; }
        public void setTarget(Long target) { this.target = target; }
        public String getRelationType() { return relationType; }
        public void setRelationType(String relationType) { this.relationType = relationType; }
    }

    /**
     * 课程信息（用于 ECharts 类别）
     */
    public static class CourseInfo {
        private Long id;
        private String name;

        public CourseInfo() {}

        public CourseInfo(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}