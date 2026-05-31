package com.qiniu.voice_calendar.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageVO {
    private Long id;
    private String role;
    private String content;
    private String intent;
    private String audioUrl;
    private String metadata;
    private LocalDateTime createdAt;
}
