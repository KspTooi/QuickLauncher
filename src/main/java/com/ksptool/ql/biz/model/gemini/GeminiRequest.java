package com.ksptool.ql.biz.model.gemini;

import lombok.Data;
import com.ksptool.ql.biz.model.po.ModelChatHistoryPo;
import java.util.ArrayList;
import java.util.List;

@Data
public class GeminiRequest {
    private List<Content> contents;
    private List<SafetySetting> safetySettings;
    private GenerationConfig generationConfig;

    @Data
    public static class Content {
        private String role;
        private List<Part> parts;
    }

    @Data
    public static class Part {
        private String text;
    }

    @Data
    public static class SafetySetting {
        private String category;
        private String threshold;
    }

    @Data
    public static class GenerationConfig {
        private List<String> stopSequences;
        private Double temperature;
        private Integer maxOutputTokens;
        private Double topP;
        private Integer topK;
    }

    public static GeminiRequest of(String text, Double temperature, Double topP, Integer topK) {
        return of(List.of(new ChatMessage("user", text)), temperature, topP, topK);
    }
    
    public static GeminiRequest of(List<ChatMessage> messages, Double temperature, Double topP, Integer topK) {
        GeminiRequest request = new GeminiRequest();
        
        // 设置对话内容
        List<Content> contents = new ArrayList<>();
        for (ChatMessage message : messages) {
            Content content = new Content();
            content.setRole(message.getRole());
            
            Part part = new Part();
            part.setText(message.getText());
            content.setParts(List.of(part));
            
            contents.add(content);
        }
        request.setContents(contents);
        
        // 设置安全配置
        SafetySetting safetySetting = new SafetySetting();
        safetySetting.setCategory("HARM_CATEGORY_DANGEROUS_CONTENT");
        safetySetting.setThreshold("BLOCK_ONLY_HIGH");
        request.setSafetySettings(List.of(safetySetting));
        
        // 设置生成配置
        GenerationConfig config = new GenerationConfig();
        config.setTemperature(temperature);
        config.setMaxOutputTokens(800);
        config.setTopP(topP);
        config.setTopK(topK);
        request.setGenerationConfig(config);
        
        return request;
    }
    
    public static GeminiRequest ofHistory(List<ModelChatHistoryPo> histories, Double temperature, Double topP, Integer topK) {
        List<ChatMessage> messages = new ArrayList<>();
        for (ModelChatHistoryPo history : histories) {
            String role = history.getRole() == 0 ? "user" : "assistant";
            messages.add(new ChatMessage(role, history.getContent()));
        }
        return of(messages, temperature, topP, topK);
    }
    
    @Data
    public static class ChatMessage {
        private String role;
        private String text;
        
        public ChatMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }
} 