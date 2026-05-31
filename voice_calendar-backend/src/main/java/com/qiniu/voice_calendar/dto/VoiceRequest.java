package com.qiniu.voice_calendar.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VoiceRequest {
    @NotBlank(message = "语音文本不能为空")
    private String text;
}
