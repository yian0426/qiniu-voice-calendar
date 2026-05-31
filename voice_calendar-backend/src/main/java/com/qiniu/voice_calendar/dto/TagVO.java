package com.qiniu.voice_calendar.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TagVO {
    private Long id;
    private String name;
    private String color;
    private Long eventCount;
}
