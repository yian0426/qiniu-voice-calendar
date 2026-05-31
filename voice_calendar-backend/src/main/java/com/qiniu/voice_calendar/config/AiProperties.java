package com.qiniu.voice_calendar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-4o";
    private String format = "openai"; // "openai" or "anthropic"
    private String authStyle = "api-key"; // "api-key" or "bearer"
    private String audioBaseUrl = ""; // 音频服务独立地址，为空则用 baseUrl
    private String asrModel = "whisper-1";
    private String ttsModel = "tts-1";
    private String ttsVoice = "alloy";
}
