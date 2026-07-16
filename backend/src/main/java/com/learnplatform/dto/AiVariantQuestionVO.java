package com.learnplatform.dto;

import java.util.ArrayList;
import java.util.List;

/** 可在作答前安全返回的结构化变式题，不包含正确答案和解析。 */
public class AiVariantQuestionVO {

    private Long id;
    private String questionType;
    private String questionContent;
    private List<Option> options = new ArrayList<>();
    private Integer difficulty;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
    public List<Option> getOptions() { return options; }
    public void setOptions(List<Option> options) { this.options = options; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }

    public static class Option {
        private String label;
        private String content;

        public Option() {}

        public Option(String label, String content) {
            this.label = label;
            this.content = content;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
