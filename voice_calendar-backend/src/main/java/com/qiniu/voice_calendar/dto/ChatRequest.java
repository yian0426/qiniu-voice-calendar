package com.qiniu.voice_calendar.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    private Long conversationId;  // null = new conversation
    @NotBlank(message = "消息内容不能为空")
    private String content;
}
