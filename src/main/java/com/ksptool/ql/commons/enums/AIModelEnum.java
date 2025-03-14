package com.ksptool.ql.commons.enums;

import lombok.Getter;

/**
 * AI模型枚举
 */
@Getter
public enum AIModelEnum {

    /*正式模型*/
    GEMINI_2_FLASH("gemini-2.0-flash", "Gemini 2.0 Flash"),
    GEMINI_2_FLASH_LITE("gemini-2.0-flash-lite", "Gemini 2.0 Flash-Lite"),
    GEMINI_15_FLASH("gemini-1.5-flash", "Gemini 1.5 Flash"),
    GEMINI_15_FLASH_8B("gemini-1.5-flash-8b", "Gemini 1.5 Flash 8B"),
    GEMINI_15_PRO("gemini-1.5-pro", "Gemini 1.5 Pro"),

    /*实验模型*/

    GEMINI_2_PRO_EXP("gemini-2.0-pro-exp", "Gemini 2.0 Pro Exp"),
    GEMINI_2_PRO_EXP_0205("gemini-2.0-pro-exp-02-05", "Gemini 2.0 Pro Exp 2025-02-05"),
    GEMINI_2_FLASH_THINKING_EXP_0121("gemini-2.0-flash-thinking-exp-01-21", "Gemini 2.0 Flash Thinking Exp 2025-01-21"),
    GEMINI_2_FLASH_THINKING_EXP_1219("gemini-2.0-flash-thinking-exp-1219", "Gemini 2.0 Flash Thinking Exp 2024-12-19"),
    GEMINI_2_FLASH_EXP("gemini-2.0-flash-exp", "Gemini 2.0 Flash Exp"),

    GEMINI_2_EXP_1206("gemini-exp-1206", "Gemini 2024-12-06"),
    GEMINI_2_EXP_1121("gemini-exp-1121", "Gemini 2024-11-21"),
    GEMINI_2_EXP_1114("gemini-exp-1114", "Gemini 2024-11-14"),

    GEMINI_15_PRO_EXP_0827("gemini-1.5-pro-exp-0827", "Gemini 1.5 Pro Exp 2024-08-27"),
    GEMINI_15_PRO_EXP_0801("gemini-1.5-pro-exp-0801", "Gemini 1.5 Pro Exp 2024-08-01"),

    GEMINI_15_PRO_001("gemini-1.5-pro-001", "Gemini 1.5 Pro #001"),
    GEMINI_15_PRO_002("gemini-1.5-pro-002", "Gemini 1.5 Pro #002"),
    GEMINI_15_FLASH_001("gemini-1.5-flash-001", "Gemini 1.5 Flash #001"),
    GEMINI_15_FLASH_002("gemini-1.5-flash-002", "Gemini 1.5 Flash #002"),


    //GROK_3("grok-3", "Grok 3"),
    GROK_2_1212("grok-2-1212", "Grok 2 #1212"),
    //GROK_2_VISION_1212("grok-2-vision-1212", "Grok 2 Vision #1212"),

    //GROK_BETA("grok-beta", "Grok Beta"),
    //GROK_BETA_VISION("grok-vision-beta", "Grok Beta Vision"),



    //目前不会计划支持DeepSeek
//    DEEPSEEK_CHAT("deepseek-chat", "DeepSeek"),
//    DEEPSEEK_REASONER("deepseek-reasoner", "DeepSeek-Reasoner(R1)"),

    ;

    /**
     * 模型代码
     */
    private final String code;
    
    /**
     * 模型名称
     */
    private final String name;
    
    AIModelEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }
    
    /**
     * 根据代码获取枚举
     */
    public static AIModelEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (AIModelEnum model : values()) {
            if (model.getCode().equals(code)) {
                return model;
            }
        }
        return null;
    }
} 