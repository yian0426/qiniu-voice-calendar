package com.qiniu.voice_calendar.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiniu.voice_calendar.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
