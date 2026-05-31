package com.qiniu.voice_calendar.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qiniu.voice_calendar.dto.CreateEventRequest;
import com.qiniu.voice_calendar.dto.EventVO;
import com.qiniu.voice_calendar.dto.PatchEventRequest;
import com.qiniu.voice_calendar.dto.StatusRequest;
import com.qiniu.voice_calendar.dto.UpdateEventRequest;
import com.qiniu.voice_calendar.exception.BusinessException;
import com.qiniu.voice_calendar.exception.GlobalExceptionHandler;
import com.qiniu.voice_calendar.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EventController 接口测试")
class EventControllerTest {

    @Mock
    private EventService eventService;

    @InjectMocks
    private EventController eventController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(eventController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        mockAuthenticatedUser(1L, "testuser");
    }

    private void mockAuthenticatedUser(Long userId, String username) {
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(userId);
        when(auth.getDetails()).thenReturn(username);
        SecurityContext ctx = org.mockito.Mockito.mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private EventVO sampleEvent(Long id) {
        return EventVO.builder()
                .id(id).title("团队站会").description("每日站会")
                .startTime(LocalDateTime.of(2026, 5, 30, 9, 30))
                .endTime(LocalDateTime.of(2026, 5, 30, 9, 45))
                .duration("15m").location("会议室A").status(0)
                .participants(List.of("张三"))
                .tags(List.of("工作"))
                .reminderBefore(5)
                .createdAt(LocalDateTime.of(2026, 5, 28, 8, 0))
                .updatedAt(LocalDateTime.of(2026, 5, 28, 8, 0))
                .build();
    }

    // ══════════════════════════════════════
    //   GET /api/events
    // ══════════════════════════════════════

    @Nested
    @DisplayName("GET /api/events — 查询事件列表")
    class ListEvents {

        @Test
        @DisplayName("正常查询返回分页数据")
        void shouldReturnPagedEvents() throws Exception {
            Page<EventVO> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(sampleEvent(1L)));
            when(eventService.listEvents(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value(1))
                    .andExpect(jsonPath("$.data.records[0].title").value("团队站会"));
        }

        @Test
        @DisplayName("带日期筛选查询")
        void shouldFilterByDateRange() throws Exception {
            Page<EventVO> page = new Page<>(1, 20, 0);
            page.setRecords(Collections.emptyList());
            when(eventService.listEvents(eq(1L), eq("2026-05-01"), eq("2026-05-31"),
                    isNull(), isNull(), isNull(), eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/events")
                            .param("startDate", "2026-05-01")
                            .param("endDate", "2026-05-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(0));
        }
    }

    // ══════════════════════════════════════
    //   GET /api/events/{id}
    // ══════════════════════════════════════

    @Nested
    @DisplayName("GET /api/events/{id} — 获取事件详情")
    class GetEvent {

        @Test
        @DisplayName("正常获取 → 200")
        void shouldReturnEvent() throws Exception {
            when(eventService.getEvent(1L, 1L)).thenReturn(sampleEvent(1L));

            mockMvc.perform(get("/api/events/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("事件不存在 → 404")
        void shouldReturn404WhenNotFound() throws Exception {
            when(eventService.getEvent(1L, 999L))
                    .thenThrow(new BusinessException(404, "事件不存在"));

            mockMvc.perform(get("/api/events/999"))
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("事件不存在"));
        }
    }

    // ══════════════════════════════════════
    //   POST /api/events
    // ══════════════════════════════════════

    @Nested
    @DisplayName("POST /api/events — 创建事件")
    class CreateEvent {

        @Test
        @DisplayName("正常创建 → 200")
        void shouldCreateEvent() throws Exception {
            CreateEventRequest req = new CreateEventRequest();
            req.setTitle("项目讨论会");
            req.setStartTime(LocalDateTime.of(2026, 5, 31, 14, 0));
            req.setEndTime(LocalDateTime.of(2026, 5, 31, 15, 30));
            req.setTags(List.of("工作", "会议"));

            EventVO created = EventVO.builder()
                    .id(10L).title("项目讨论会")
                    .startTime(LocalDateTime.of(2026, 5, 31, 14, 0))
                    .endTime(LocalDateTime.of(2026, 5, 31, 15, 30))
                    .duration("1h 30m").status(0).tags(List.of("工作", "会议"))
                    .build();

            when(eventService.createEvent(eq(1L), any(CreateEventRequest.class))).thenReturn(created);

            mockMvc.perform(post("/api/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("创建成功"))
                    .andExpect(jsonPath("$.data.id").value(10));
        }

        @Test
        @DisplayName("标题为空 → 400")
        void shouldRejectEmptyTitle() throws Exception {
            CreateEventRequest req = new CreateEventRequest();
            req.setTitle("");
            req.setStartTime(LocalDateTime.now());
            req.setEndTime(LocalDateTime.now().plusHours(1));

            mockMvc.perform(post("/api/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("缺少开始时间 → 400")
        void shouldRejectMissingStartTime() throws Exception {
            CreateEventRequest req = new CreateEventRequest();
            req.setTitle("test");
            req.setEndTime(LocalDateTime.now().plusHours(1));

            mockMvc.perform(post("/api/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ══════════════════════════════════════
    //   PUT /api/events/{id}
    // ══════════════════════════════════════

    @Nested
    @DisplayName("PUT /api/events/{id} — 全量更新事件")
    class UpdateEvent {

        @Test
        @DisplayName("正常更新 → 200")
        void shouldUpdateEvent() throws Exception {
            UpdateEventRequest req = new UpdateEventRequest();
            req.setTitle("改期会议");
            req.setStartTime(LocalDateTime.of(2026, 6, 1, 14, 0));
            req.setEndTime(LocalDateTime.of(2026, 6, 1, 15, 30));

            EventVO updated = EventVO.builder()
                    .id(1L).title("改期会议")
                    .startTime(LocalDateTime.of(2026, 6, 1, 14, 0))
                    .endTime(LocalDateTime.of(2026, 6, 1, 15, 30))
                    .duration("1h 30m").status(0)
                    .build();

            when(eventService.updateEvent(eq(1L), eq(1L), any(UpdateEventRequest.class))).thenReturn(updated);

            mockMvc.perform(put("/api/events/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("修改成功"));
        }
    }

    // ══════════════════════════════════════
    //   PATCH /api/events/{id}
    // ══════════════════════════════════════

    @Nested
    @DisplayName("PATCH /api/events/{id} — 部分更新事件")
    class PatchEvent {

        @Test
        @DisplayName("只改标题 → 200")
        void shouldPatchTitleOnly() throws Exception {
            PatchEventRequest req = new PatchEventRequest();
            req.setTitle("新标题");

            EventVO patched = EventVO.builder().id(1L).title("新标题")
                    .startTime(LocalDateTime.of(2026, 5, 30, 9, 30))
                    .endTime(LocalDateTime.of(2026, 5, 30, 9, 45))
                    .duration("15m").status(0).build();

            when(eventService.patchEvent(eq(1L), eq(1L), any(PatchEventRequest.class))).thenReturn(patched);

            mockMvc.perform(patch("/api/events/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("新标题"));
        }
    }

    // ══════════════════════════════════════
    //   DELETE /api/events/{id}
    // ══════════════════════════════════════

    @Nested
    @DisplayName("DELETE /api/events/{id} — 删除事件")
    class DeleteEvent {

        @Test
        @DisplayName("正常删除 → 200")
        void shouldDeleteEvent() throws Exception {
            mockMvc.perform(delete("/api/events/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("已删除"));
        }
    }

    // ══════════════════════════════════════
    //   PATCH /api/events/{id}/status
    // ══════════════════════════════════════

    @Nested
    @DisplayName("PATCH /api/events/{id}/status — 切换完成状态")
    class ToggleStatus {

        @Test
        @DisplayName("标记完成 → 200")
        void shouldMarkCompleted() throws Exception {
            EventVO completed = EventVO.builder().id(1L).title("团队站会").status(1).build();
            when(eventService.toggleStatus(1L, 1L, 1)).thenReturn(completed);

            StatusRequest req = new StatusRequest();
            req.setStatus(1);

            mockMvc.perform(patch("/api/events/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("已标记为已完成"))
                    .andExpect(jsonPath("$.data.status").value(1));
        }

        @Test
        @DisplayName("标记未完成 → 200")
        void shouldMarkUncompleted() throws Exception {
            EventVO uncompleted = EventVO.builder().id(1L).title("团队站会").status(0).build();
            when(eventService.toggleStatus(1L, 1L, 0)).thenReturn(uncompleted);

            StatusRequest req = new StatusRequest();
            req.setStatus(0);

            mockMvc.perform(patch("/api/events/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("已标记为未完成"))
                    .andExpect(jsonPath("$.data.status").value(0));
        }

        @Test
        @DisplayName("非法状态值 → 400")
        void shouldRejectInvalidStatus() throws Exception {
            String body = "{\"status\": 2}";

            mockMvc.perform(patch("/api/events/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }
}
