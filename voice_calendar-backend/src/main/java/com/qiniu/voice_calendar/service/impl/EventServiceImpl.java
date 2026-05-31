package com.qiniu.voice_calendar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.voice_calendar.dto.CreateEventRequest;
import com.qiniu.voice_calendar.dto.EventVO;
import com.qiniu.voice_calendar.dto.PatchEventRequest;
import com.qiniu.voice_calendar.dto.UpdateEventRequest;
import com.qiniu.voice_calendar.entity.Event;
import com.qiniu.voice_calendar.entity.EventTag;
import com.qiniu.voice_calendar.entity.Tag;
import com.qiniu.voice_calendar.exception.BusinessException;
import com.qiniu.voice_calendar.mapper.EventMapper;
import com.qiniu.voice_calendar.mapper.EventTagMapper;
import com.qiniu.voice_calendar.mapper.TagMapper;
import com.qiniu.voice_calendar.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventMapper eventMapper;
    private final TagMapper tagMapper;
    private final EventTagMapper eventTagMapper;
    private final ObjectMapper objectMapper;

    // ──────────────────────────────────────
    //   List with filters + pagination
    // ──────────────────────────────────────

    @Override
    public Page<EventVO> listEvents(Long userId, String startDate, String endDate,
                                    Integer status, String tag, String keyword,
                                    int page, int size) {
        LambdaQueryWrapper<Event> qw = new LambdaQueryWrapper<Event>()
                .eq(Event::getUserId, userId);

        if (startDate != null && !startDate.isBlank()) {
            qw.ge(Event::getStartTime, LocalDate.parse(startDate).atStartOfDay());
        }
        if (endDate != null && !endDate.isBlank()) {
            qw.le(Event::getStartTime, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        }
        if (status != null) {
            qw.eq(Event::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(Event::getTitle, keyword).or().like(Event::getDescription, keyword));
        }

        // tag filter: find matching event IDs via join
        if (tag != null && !tag.isBlank()) {
            List<Long> eventIds = findEventIdsByTag(userId, tag);
            if (eventIds.isEmpty()) {
                Page<EventVO> empty = new Page<>(page, size);
                empty.setRecords(Collections.emptyList());
                empty.setTotal(0);
                return empty;
            }
            qw.in(Event::getId, eventIds);
        }

        qw.orderByDesc(Event::getStartTime);

        Page<Event> raw = eventMapper.selectPage(new Page<>(page, size), qw);

        Page<EventVO> result = new Page<>(page, size, raw.getTotal());
        result.setRecords(raw.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList()));
        return result;
    }

    // ──────────────────────────────────────
    //   Get by ID (with ownership check)
    // ──────────────────────────────────────

    @Override
    public EventVO getEvent(Long userId, Long eventId) {
        Event event = findOwnEvent(userId, eventId);
        return toVO(event);
    }

    // ──────────────────────────────────────
    //   Create
    // ──────────────────────────────────────

    @Override
    @Transactional
    public EventVO createEvent(Long userId, CreateEventRequest request) {
        validateTime(request.getStartTime(), request.getEndTime());

        Event event = new Event();
        event.setUserId(userId);
        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setDuration(calcDuration(request.getStartTime(), request.getEndTime(), request.getDuration()));
        event.setLocation(request.getLocation());
        event.setStatus(0);
        event.setParticipants(toJson(request.getParticipants()));
        event.setReminderBefore(request.getReminderBefore());
        eventMapper.insert(event);

        syncTags(userId, event.getId(), request.getTags());

        log.info("Event created: id={}, userId={}, title={}", event.getId(), userId, event.getTitle());
        return toVO(event);
    }

    // ──────────────────────────────────────
    //   Full update
    // ──────────────────────────────────────

    @Override
    @Transactional
    public EventVO updateEvent(Long userId, Long eventId, UpdateEventRequest request) {
        Event event = findOwnEvent(userId, eventId);
        validateTime(request.getStartTime(), request.getEndTime());

        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setDuration(calcDuration(request.getStartTime(), request.getEndTime(), request.getDuration()));
        event.setLocation(request.getLocation());
        event.setParticipants(toJson(request.getParticipants()));
        event.setReminderBefore(request.getReminderBefore());
        eventMapper.updateById(event);

        syncTags(userId, event.getId(), request.getTags());

        log.info("Event updated: id={}, userId={}", eventId, userId);
        return toVO(event);
    }

    // ──────────────────────────────────────
    //   Partial update
    // ──────────────────────────────────────

    @Override
    @Transactional
    public EventVO patchEvent(Long userId, Long eventId, PatchEventRequest request) {
        Event event = findOwnEvent(userId, eventId);

        if (request.getTitle() != null) event.setTitle(request.getTitle().trim());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getStartTime() != null) event.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) event.setEndTime(request.getEndTime());

        // Re-validate time if either field changed
        if (request.getStartTime() != null || request.getEndTime() != null) {
            validateTime(event.getStartTime(), event.getEndTime());
        }
        if (request.getDuration() != null) {
            event.setDuration(request.getDuration());
        } else if (request.getStartTime() != null || request.getEndTime() != null) {
            event.setDuration(calcDuration(event.getStartTime(), event.getEndTime(), null));
        }

        if (request.getLocation() != null) event.setLocation(request.getLocation());
        if (request.getParticipants() != null) event.setParticipants(toJson(request.getParticipants()));
        if (request.getReminderBefore() != null) event.setReminderBefore(request.getReminderBefore());
        eventMapper.updateById(event);

        if (request.getTags() != null) {
            syncTags(userId, event.getId(), request.getTags());
        }

        log.info("Event patched: id={}, userId={}", eventId, userId);
        return toVO(event);
    }

    // ──────────────────────────────────────
    //   Delete
    // ──────────────────────────────────────

    @Override
    @Transactional
    public void deleteEvent(Long userId, Long eventId) {
        findOwnEvent(userId, eventId); // verify ownership
        eventMapper.deleteById(eventId);
        log.info("Event deleted: id={}, userId={}", eventId, userId);
    }

    // ──────────────────────────────────────
    //   Toggle status
    // ──────────────────────────────────────

    @Override
    @Transactional
    public EventVO toggleStatus(Long userId, Long eventId, int status) {
        if (status != 0 && status != 1) {
            throw new BusinessException(400, "状态值必须为 0 或 1");
        }
        Event event = findOwnEvent(userId, eventId);
        event.setStatus(status);
        eventMapper.updateById(event);
        return toVO(event);
    }

    // ══════════════════════════════════════
    //   Private helpers
    // ══════════════════════════════════════

    private Event findOwnEvent(Long userId, Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(404, "事件不存在");
        }
        if (!event.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该事件");
        }
        return event;
    }

    private void validateTime(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BusinessException(400, "结束时间必须大于开始时间");
        }
    }

    private String calcDuration(LocalDateTime start, LocalDateTime end, String provided) {
        if (provided != null && !provided.isBlank()) return provided;
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 60) return minutes + "m";
        long h = minutes / 60;
        long m = minutes % 60;
        return m > 0 ? h + "h " + m + "m" : h + "h";
    }

    /** Sync tag associations. Auto-creates tags that don't exist for the user. */
    private void syncTags(Long userId, Long eventId, List<String> tagNames) {
        // Remove old associations
        eventTagMapper.delete(new LambdaQueryWrapper<EventTag>().eq(EventTag::getEventId, eventId));

        if (tagNames == null || tagNames.isEmpty()) return;

        for (String name : tagNames) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;

            // Find or create tag
            Tag tag = tagMapper.selectOne(
                    new LambdaQueryWrapper<Tag>().eq(Tag::getUserId, userId).eq(Tag::getName, trimmed));
            if (tag == null) {
                tag = new Tag();
                tag.setUserId(userId);
                tag.setName(trimmed);
                tag.setColor(defaultTagColor(trimmed));
                tagMapper.insert(tag);
            }

            EventTag et = new EventTag();
            et.setEventId(eventId);
            et.setTagId(tag.getId());
            eventTagMapper.insert(et);
        }
    }

    private String defaultTagColor(String name) {
        // Simple deterministic color mapping based on hash
        String[] colors = {"#409eff", "#67c23a", "#e6a23c", "#f56c6c", "#909399", "#8e71dd", "#36cfc9"};
        int idx = Math.abs(name.hashCode()) % colors.length;
        return colors[idx];
    }

    private List<Long> findEventIdsByTag(Long userId, String tagName) {
        Tag tag = tagMapper.selectOne(
                new LambdaQueryWrapper<Tag>().eq(Tag::getUserId, userId).eq(Tag::getName, tagName));
        if (tag == null) return Collections.emptyList();

        List<EventTag> links = eventTagMapper.selectList(
                new LambdaQueryWrapper<EventTag>().eq(EventTag::getTagId, tag.getId()));
        return links.stream().map(EventTag::getEventId).collect(Collectors.toList());
    }

    private List<String> loadTags(Long eventId) {
        List<EventTag> links = eventTagMapper.selectList(
                new LambdaQueryWrapper<EventTag>().eq(EventTag::getEventId, eventId));
        if (links.isEmpty()) return Collections.emptyList();

        Set<Long> tagIds = links.stream().map(EventTag::getTagId).collect(Collectors.toSet());
        List<Tag> tags = tagMapper.selectBatchIds(tagIds);
        return tags.stream().map(Tag::getName).collect(Collectors.toList());
    }

    private EventVO toVO(Event event) {
        return EventVO.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .duration(event.getDuration())
                .location(event.getLocation())
                .status(event.getStatus())
                .participants(parseParticipants(event.getParticipants()))
                .tags(loadTags(event.getId()))
                .reminderBefore(event.getReminderBefore())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize participants: {}", list, e);
            return null;
        }
    }

    private List<String> parseParticipants(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize participants: {}", json, e);
            return Collections.emptyList();
        }
    }
}
