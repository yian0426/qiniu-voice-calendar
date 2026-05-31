package com.qiniu.voice_calendar.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EventVO {
    private Long id;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String duration;
    private String location;
    private Integer status;
    private List<String> participants;
    private List<String> tags;
    private Integer reminderBefore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
