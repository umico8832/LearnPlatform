package com.learnplatform.service.ai;

import java.util.function.Consumer;

/**
 * AI Provider 接口
 */
public interface AiProvider {

    /**
     * 获取当前调用线程最近一次上游返回的 token 用量；上游未提供时返回 null。
     */
    default AiTokenUsage getLastTokenUsage() {
        return null;
    }

    /**
     * 调用 AI 生成回复
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return AI 回复内容（Markdown 格式）
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * 调用 AI 并逐段返回生成内容。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param onContent    内容分片回调
     */
    void chatStream(String systemPrompt, String userPrompt, Consumer<String> onContent);
}
