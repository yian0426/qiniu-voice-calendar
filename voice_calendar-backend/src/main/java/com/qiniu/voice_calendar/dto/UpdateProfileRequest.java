package com.qiniu.voice_calendar.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String email;
    private String avatarUrl;
}
