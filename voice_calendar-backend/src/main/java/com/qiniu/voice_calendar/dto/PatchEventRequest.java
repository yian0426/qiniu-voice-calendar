package com.qiniu.voice_calendar.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Partial update. Only non-null fields are updated. */
@Data
public class PatchEventRequest {
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String duration;
    private String location;
    private List<String> participants;
    private List<String> tags;
    private Integer reminderBefore;
}
