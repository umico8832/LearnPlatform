package com.learnplatform.dto;

/**
 * AI 响应 VO
 */
public class AiResponse {

    /** AI 生成的内容（Markdown 格式） */
    private String content;

    /** 来源标识 */
    private String source;

    public AiResponse() {}

    public AiResponse(String content, String source) {
        this.content = content;
        this.source = source;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}