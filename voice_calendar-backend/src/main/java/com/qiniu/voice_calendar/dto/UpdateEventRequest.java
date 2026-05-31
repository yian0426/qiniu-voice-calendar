package com.qiniu.voice_calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Full replacement update. Same fields as Create. */
@Data
public class UpdateEventRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    private String description;
    private String duration;
    private String location;
    private List<String> participants;
    private List<String> tags;
    private Integer reminderBefore;
}
