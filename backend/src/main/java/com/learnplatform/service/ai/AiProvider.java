package com.learnplatform.service.ai;

/**
 * AI Provider 接口
 */
public interface AiProvider {

    /**
     * 调用 AI 生成回复
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return AI 回复内容（Markdown 格式）
     */
    String chat(String systemPrompt, String userPrompt);
}