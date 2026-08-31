package com.learnplatform.service.ai;

import com.learnplatform.dto.AiAssetType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class QuestionAssetPromptFactoryTest {

    @Test
    void buildUsesExpectedUserPromptForEveryAssetType() {
        String context = "question-context";
        Map<AiAssetType, String> prefixes = Map.of(
                AiAssetType.FULL_EXPLANATION, "请解析这道题目：\n\n",
                AiAssetType.BEGINNER_EXPLANATION, "请用最简单的方式解析这道题目：\n\n",
                AiAssetType.STEP_BY_STEP, "请拆解这道题目的解题步骤：\n\n",
                AiAssetType.WRONG_OPTION_ANALYSIS, "请分析这道题目的错误选项：\n\n",
                AiAssetType.COMMON_MISTAKES, "请分析这道题目的常见误区：\n\n",
                AiAssetType.VARIANT, "基于以下题目生成变式题：\n\n",
                AiAssetType.VISUAL_INTERACTIVE, "请为以下题目生成可视化讲解数据（严格输出 JSON）：\n\n");

        prefixes.forEach((assetType, prefix) -> {
            QuestionAssetPromptFactory.Prompt prompt = QuestionAssetPromptFactory.build(assetType, context);
            assertFalse(prompt.systemPrompt().isBlank());
            assertEquals(prefix + context, prompt.userPrompt());
        });
    }

    @Test
    void systemPromptsKeepExactContentAndWhitespace() throws NoSuchAlgorithmException {
        Map<AiAssetType, String> expectedHashes = Map.of(
                AiAssetType.FULL_EXPLANATION, "1047cf5eba671ff7c162595c797908b22d50d17617362fc76340e65a915dfad4",
                AiAssetType.BEGINNER_EXPLANATION, "729265e7308344e25a45885c8630711dffad6318f04c2e9f778b57cb64ee7be3",
                AiAssetType.STEP_BY_STEP, "4a8729f86c49f6e44123cc8be2659c6443bd9857060010b2394b7695d8e680fa",
                AiAssetType.WRONG_OPTION_ANALYSIS, "d1b0fd3b0b87dd76c80fc7499a11f23c9a459e2267ffdf395c006d523ebebdf2",
                AiAssetType.COMMON_MISTAKES, "6adc8b0b2f29f703a257964a64e948b2d5390646c8e9d747ec30dc0707b919d1",
                AiAssetType.VARIANT, "122d3dc5c30024c14d1e40ea6257d4a6ead3a040a169527003a261bb0471c0f1",
                AiAssetType.VISUAL_INTERACTIVE, "b3faa021b2a7694c9971fcccc4c86bc0b724bea5548d8008dd7a77726a7a9a85");

        for (Map.Entry<AiAssetType, String> entry : expectedHashes.entrySet()) {
            String systemPrompt = QuestionAssetPromptFactory.build(entry.getKey(), "context").systemPrompt();
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(systemPrompt.getBytes(StandardCharsets.UTF_8));
            assertEquals(entry.getValue(), HexFormat.of().formatHex(digest), entry.getKey().name());
        }
    }

    @Test
    void visualInteractivePromptContainsMermaidInstructions() {
        String systemPrompt = visualSystemPrompt();

        assertContains(systemPrompt, "mermaid");
        assertContains(systemPrompt, "Mermaid");
        assertContains(systemPrompt, "flowchart");
        assertContains(systemPrompt, "SQL");
        assertContains(systemPrompt, "mermaid code 必须是合法的 Mermaid 语法");
        assertContains(systemPrompt, "`mermaid`：Mermaid 流程图");
    }

    @Test
    void visualInteractivePromptContainsCodeAnimationInstructions() {
        String systemPrompt = visualSystemPrompt();

        assertContains(systemPrompt, "code_animation");
        assertContains(systemPrompt, "代码执行动画");
        assertContains(systemPrompt, "lineStart");
        assertContains(systemPrompt, "lineEnd");
        assertContains(systemPrompt, "variables");
        assertContains(systemPrompt, "changed");
        assertContains(systemPrompt, "lineStart/lineEnd 从 1 开始计数");
        assertContains(systemPrompt, "code 字段必须是完整可执行的代码");
    }

    @Test
    void visualInteractivePromptContainsSqlExecutionInstructions() {
        String systemPrompt = visualSystemPrompt();

        assertContains(systemPrompt, "sql_execution");
        assertContains(systemPrompt, "SQL 执行顺序可视化");
        assertContains(systemPrompt, "resultHeaders");
        assertContains(systemPrompt, "resultRows");
        assertContains(systemPrompt, "rowCount");
        assertContains(systemPrompt, "clause");
        assertContains(systemPrompt, "finalResult");
        assertContains(systemPrompt, "FROM/JOIN → WHERE → GROUP BY → HAVING → SELECT → DISTINCT → ORDER BY → LIMIT/OFFSET");
        assertContains(systemPrompt, "优先使用 sql_execution 展示执行顺序");
    }

    @Test
    void visualInteractivePromptContainsNetworkProtocolInstructions() {
        String systemPrompt = visualSystemPrompt();

        assertContains(systemPrompt, "network_protocol");
        assertContains(systemPrompt, "网络协议交互过程可视化");
        assertContains(systemPrompt, "entities");
        assertContains(systemPrompt, "messages");
        assertContains(systemPrompt, "TCP 三次握手");
        assertContains(systemPrompt, "HTTP");
        assertContains(systemPrompt, "DNS");
        assertContains(systemPrompt, "network_protocol 的 entities 必须按从左到右的排列顺序给出");
        assertContains(systemPrompt, "messages 必须按时间顺序排列");
        assertContains(systemPrompt, "网络协议类题目");
        assertContains(systemPrompt, "优先使用 network_protocol");
    }

    @Test
    void visualInteractivePromptContainsOsProcessInstructions() {
        String systemPrompt = visualSystemPrompt();

        assertContains(systemPrompt, "os_process");
        assertContains(systemPrompt, "操作系统过程可视化");
        assertContains(systemPrompt, "ganttChart");
        assertContains(systemPrompt, "进程调度算法");
        assertContains(systemPrompt, "FCFS");
        assertContains(systemPrompt, "SJF");
        assertContains(systemPrompt, "RR");
        assertContains(systemPrompt, "页面置换算法");
        assertContains(systemPrompt, "LRU");
        assertContains(systemPrompt, "running");
        assertContains(systemPrompt, "ready");
        assertContains(systemPrompt, "waiting/blocked");
        assertContains(systemPrompt, "terminated");
        assertContains(systemPrompt, "ganttChart 中的 start/end 是时间刻度");
        assertContains(systemPrompt, "操作系统类题目");
        assertContains(systemPrompt, "优先使用 os_process");
    }

    private String visualSystemPrompt() {
        return QuestionAssetPromptFactory.build(AiAssetType.VISUAL_INTERACTIVE, "context").systemPrompt();
    }

    private void assertContains(String text, String expected) {
        if (!text.contains(expected)) {
            throw new AssertionError("Expected text to contain '" + expected + "' but was: " + text);
        }
    }
}
