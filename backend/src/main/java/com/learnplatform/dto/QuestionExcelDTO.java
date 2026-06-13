package com.learnplatform.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;

/**
 * 题目 Excel 导入/导出 DTO
 * 
 * Excel 列顺序：题干、题型、课程名称、难度、选项、正确答案、解析、分值、标签、知识点(逗号分隔)
 */
public class QuestionExcelDTO {

    @ExcelProperty("题干")
    @ColumnWidth(50)
    private String content;

    @ExcelProperty("题型")
    @ColumnWidth(15)
    private String questionType;

    @ExcelProperty("课程名称")
    @ColumnWidth(20)
    private String courseName;

    @ExcelProperty("难度")
    @ColumnWidth(10)
    private Integer difficulty;

    /**
     * 选项格式：A.选项内容|B.选项内容|C.选项内容|D.选项内容
     */
    @ExcelProperty("选项")
    @ColumnWidth(60)
    private String options;

    /**
     * 正确答案：单选填 A，多选填 A,B，判断填 对/错，填空/简答填参考答案
     */
    @ExcelProperty("正确答案")
    @ColumnWidth(20)
    private String answer;

    @ExcelProperty("解析")
    @ColumnWidth(50)
    private String analysis;

    @ExcelProperty("分值")
    @ColumnWidth(10)
    private Integer score;

    @ExcelProperty("标签")
    @ColumnWidth(20)
    private String tags;

    /**
     * 知识点名称，多个用逗号分隔
     */
    @ExcelProperty("知识点")
    @ColumnWidth(30)
    private String knowledgePoints;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getKnowledgePoints() { return knowledgePoints; }
    public void setKnowledgePoints(String knowledgePoints) { this.knowledgePoints = knowledgePoints; }
}