package com.qiniu.voice_calendar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("events")
public class Event {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String duration;
    private String location;
    private Integer status;       // 0=未完成, 1=已完成
    private String participants;  // JSON array string: ["张三","李四"]
    private Integer reminderBefore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Non-persistent field: tag names joined from event_tags + tags */
    @TableField(exist = false)
    private java.util.List<String> tags;
}
