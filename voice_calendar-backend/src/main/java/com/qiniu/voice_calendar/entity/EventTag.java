package com.qiniu.voice_calendar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("event_tags")
public class EventTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private Long tagId;
}
