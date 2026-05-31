package com.qiniu.voice_calendar.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiniu.voice_calendar.dto.CreateEventRequest;
import com.qiniu.voice_calendar.dto.EventVO;
import com.qiniu.voice_calendar.dto.PatchEventRequest;
import com.qiniu.voice_calendar.dto.UpdateEventRequest;

public interface EventService {
    Page<EventVO> listEvents(Long userId, String startDate, String endDate,
                             Integer status, String tag, String keyword,
                             int page, int size);

    EventVO getEvent(Long userId, Long eventId);

    EventVO createEvent(Long userId, CreateEventRequest request);

    EventVO updateEvent(Long userId, Long eventId, UpdateEventRequest request);

    EventVO patchEvent(Long userId, Long eventId, PatchEventRequest request);

    void deleteEvent(Long userId, Long eventId);

    EventVO toggleStatus(Long userId, Long eventId, int status);
}
