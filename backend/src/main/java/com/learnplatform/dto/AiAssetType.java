package com.learnplatform.dto;

/**
 * AI 学习资产类型枚举
 */
public enum AiAssetType {

    /** 标准结构化解析 */
    FULL_EXPLANATION("标准解析"),

    /** 小白版解析（少术语多铺垫） */
    BEGINNER_EXPLANATION("小白版解析"),

    /** 步骤拆解 */
    STEP_BY_STEP("步骤拆解"),

    /** 错误选项分析 */
    WRONG_OPTION_ANALYSIS("错误选项分析"),

    /** 常见误区 */
    COMMON_MISTAKES("常见误区"),

    /** 变式题 */
    VARIANT("变式题"),

    /** 可视化交互讲解（结构化 JSON 渲染） */
    VISUAL_INTERACTIVE("可视化讲解");

    private final String label;

    AiAssetType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}