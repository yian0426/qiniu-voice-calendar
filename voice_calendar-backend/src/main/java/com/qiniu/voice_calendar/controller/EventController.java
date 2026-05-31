package com.qiniu.voice_calendar.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiniu.voice_calendar.common.Result;
import com.qiniu.voice_calendar.dto.CreateEventRequest;
import com.qiniu.voice_calendar.dto.EventVO;
import com.qiniu.voice_calendar.dto.PatchEventRequest;
import com.qiniu.voice_calendar.dto.StatusRequest;
import com.qiniu.voice_calendar.dto.UpdateEventRequest;
import com.qiniu.voice_calendar.service.EventService;
import com.qiniu.voice_calendar.util.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public Result<Page<EventVO>> listEvents(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = SecurityContextUtil.getCurrentUserId();
        Page<EventVO> result = eventService.listEvents(userId, startDate, endDate, status, tag, keyword, page, size);
        return Result.ok(result);
    }

    @GetMapping("/{id}")
    public Result<EventVO> getEvent(@PathVariable Long id) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        return Result.ok(eventService.getEvent(userId, id));
    }

    @PostMapping
    public Result<EventVO> createEvent(@Valid @RequestBody CreateEventRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        EventVO event = eventService.createEvent(userId, request);
        return Result.ok("创建成功", event);
    }

    @PutMapping("/{id}")
    public Result<EventVO> updateEvent(@PathVariable Long id, @Valid @RequestBody UpdateEventRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        EventVO event = eventService.updateEvent(userId, id, request);
        return Result.ok("修改成功", event);
    }

    @PatchMapping("/{id}")
    public Result<EventVO> patchEvent(@PathVariable Long id, @RequestBody PatchEventRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        EventVO event = eventService.patchEvent(userId, id, request);
        return Result.ok("修改成功", event);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteEvent(@PathVariable Long id) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        eventService.deleteEvent(userId, id);
        return Result.ok("已删除");
    }

    @PatchMapping("/{id}/status")
    public Result<EventVO> toggleStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        EventVO event = eventService.toggleStatus(userId, id, request.getStatus());
        String msg = request.getStatus() == 1 ? "已标记为已完成" : "已标记为未完成";
        return Result.ok(msg, event);
    }
}
