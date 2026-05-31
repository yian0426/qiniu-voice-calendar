package com.qiniu.voice_calendar.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.voice_calendar.dto.*;
import com.qiniu.voice_calendar.service.AiService;
import com.qiniu.voice_calendar.service.EventService;
import com.qiniu.voice_calendar.service.StreamEvent;
import com.qiniu.voice_calendar.service.VoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * 语音处理服务实现 — 无状态，每次请求独立处理。
 * 使用 MiMo AI 的 tool calling 能力解析语音文本为日程操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceServiceImpl implements VoiceService {

    private final AiService aiService;
    private final EventService eventService;
    private final ObjectMapper objectMapper;

    @Override
    public void processVoice(Long userId, String text, Consumer<String> sseSender) {
        log.info("[voice] 处理用户 {} 的语音输入: {}", userId, truncate(text, 80));

        // 构建 LLM 消息（无历史，单轮处理）
        List<AiService.ChatMessage> llmMessages = buildVoiceMessages(text);
        List<Map<String, Object>> tools = buildToolDefinitions();

        var assistantContent = new StringBuilder();
        var toolCalls = new ArrayList<StreamEvent>();

        // 第一次 LLM 调用（带 tools）
        log.info("[voice] 调用 MiMo AI (带 tools, {} 个工具)", tools.size());
        aiService.streamChat(llmMessages, tools, event -> {
            switch (event.type()) {
                case CONTENT -> {
                    assistantContent.append(event.content());
                    sendSse(sseSender, "content", event.content());
                }
                case TOOL_CALL -> {
                    toolCalls.add(event);
                    log.info("[voice] 工具调用: {} args={}", event.toolName(), event.arguments());
                    sendSse(sseSender, "status", "正在执行: " + describeToolCall(event.toolName()));
                }
                case ERROR -> {
                    log.warn("[voice] LLM 流错误: {}", event.content());
                    sendSse(sseSender, "error", event.content() != null ? event.content() : "未知错误");
                }
                case DONE -> log.debug("[voice] 第一次 LLM 流完成");
            }
        });
        log.info("[voice] LLM 调用结束, contentLen={}, toolCalls={}", assistantContent.length(), toolCalls.size());

        // 如果有工具调用，执行并做第二次 LLM 调用
        if (!toolCalls.isEmpty()) {
            sendSse(sseSender, "status", "正在理解你的意图...");

            // 添加 assistant tool_call 消息到上下文
            for (var tc : toolCalls) {
                llmMessages.add(new AiService.ChatMessage("assistant", tc.arguments(), tc.toolCallId(), tc.toolName()));
            }

            // 执行工具调用
            for (var tc : toolCalls) {
                sendSse(sseSender, "status", "正在执行: " + describeToolCall(tc.toolName()));
                String result = executeToolCall(userId, tc.toolName(), tc.arguments(), sseSender);
                sendSse(sseSender, "tool_result", Map.of(
                        "toolName", tc.toolName(),
                        "result", result
                ));
                llmMessages.add(new AiService.ChatMessage("tool", result, tc.toolCallId(), null));
            }

            // 第二次 LLM 调用 — 生成自然语言回复
            sendSse(sseSender, "status", "正在生成回复...");
            log.info("[voice] 第二次 LLM 调用 (无 tools)");
            aiService.streamChat(llmMessages, null, event -> {
                switch (event.type()) {
                    case CONTENT -> {
                        assistantContent.append(event.content());
                        sendSse(sseSender, "content", event.content());
                    }
                    case ERROR -> {
                        log.warn("[voice] 第二次 LLM 流错误: {}", event.content());
                        sendSse(sseSender, "error", event.content() != null ? event.content() : "未知错误");
                    }
                    case DONE -> log.debug("[voice] 第二次 LLM 流完成");
                    default -> {}
                }
            });
            log.info("[voice] 第二次 LLM 调用结束, contentLen={}", assistantContent.length());
        }

        // 发送完成信号
        log.info("[voice] 发送 done 信号");
        sendSse(sseSender, "done", Map.of());
        log.info("[voice] 用户 {} 的语音处理完成", userId);
    }

    // ── 构建语音专用 LLM 消息 ──

    private List<AiService.ChatMessage> buildVoiceMessages(String text) {
        List<AiService.ChatMessage> result = new ArrayList<>();

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"));
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String tomorrow = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dayAfterTomorrow = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        result.add(new AiService.ChatMessage("system",
                "你是七牛语音日历的语音助手。用户通过语音输入指令，你需要理解并执行日程操作。\n\n" +
                "【重要】当前北京时间: " + now + "，今天是 " + today + "，明天是 " + tomorrow + "，后天是 " + dayAfterTomorrow + "。\n" +
                "当用户说'今天'时使用日期 " + today + "，说'明天'时使用 " + tomorrow + "，说'后天'时使用 " + dayAfterTomorrow + "。\n" +
                "如果用户说'下午3点'，理解为当天15:00。如果说'下周一'，计算出具体日期。\n" +
                "如果没有明确指定结束时间，默认事件时长为1小时。\n\n" +
                "请根据用户意图调用对应的工具来创建、查询、修改或删除日历事件。\n" +
                "回复要简洁友好，像一个贴心的日历管家。\n\n" +
                "【输出规则】当你成功创建或修改了日程事件后，你的回复必须包含以下结构化数据块：\n" +
                "在自然语言确认之后，另起一行输出 ```calendar-json 代码块，格式如：\n" +
                "```calendar-json\n" +
                "{\"action\":\"create\",\"events\":[{\"id\":1,\"title\":\"会议\",\"startTime\":\"" + today + "T15:00:00\",\"endTime\":\"" + today + "T16:00:00\"}]}\n" +
                "```\n" +
                "action 可选值: create / update / delete / query。\n" +
                "如果没有日程操作，不要输出此代码块。"));

        result.add(new AiService.ChatMessage("user", text));
        return result;
    }

    // ── 工具定义（与 ChatService 相同） ──

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildToolDefinitions() {
        return List.of(
                Map.of(
                        "name", "create_event",
                        "description", "创建新的日历事件。当用户说'帮我添加'、'创建'、'安排'、'提醒我'等时使用。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "title", Map.of("type", "string", "description", "事件标题"),
                                        "description", Map.of("type", "string", "description", "事件描述"),
                                        "startTime", Map.of("type", "string", "description", "开始时间，ISO格式如 2026-05-31T15:00:00"),
                                        "endTime", Map.of("type", "string", "description", "结束时间，ISO格式如 2026-05-31T16:00:00"),
                                        "location", Map.of("type", "string", "description", "地点"),
                                        "participants", Map.of("type", "array", "items", Map.of("type", "string"), "description", "参与者列表"),
                                        "tags", Map.of("type", "array", "items", Map.of("type", "string"), "description", "标签列表")
                                ),
                                "required", List.of("title", "startTime", "endTime")
                        )
                ),
                Map.of(
                        "name", "list_events",
                        "description", "查询日历事件。当用户说'我今天有什么安排'、'查看日程'、'最近有什么事件'时使用。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "startDate", Map.of("type", "string", "description", "开始日期，如 2026-05-31"),
                                        "endDate", Map.of("type", "string", "description", "结束日期"),
                                        "status", Map.of("type", "integer", "description", "0=未完成, 1=已完成")
                                ),
                                "required", List.of()
                        )
                ),
                Map.of(
                        "name", "update_event",
                        "description", "修改已有的日历事件。当用户说'修改'、'改一下'、'推迟'、'提前'、'换个时间'时使用。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "eventId", Map.of("type", "integer", "description", "要修改的事件ID"),
                                        "title", Map.of("type", "string", "description", "新标题"),
                                        "startTime", Map.of("type", "string", "description", "新开始时间"),
                                        "endTime", Map.of("type", "string", "description", "新结束时间"),
                                        "location", Map.of("type", "string", "description", "新地点")
                                ),
                                "required", List.of("eventId")
                        )
                ),
                Map.of(
                        "name", "delete_event",
                        "description", "删除日历事件。当用户说'删除'、'取消'、'去掉'某个事件时使用。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "eventId", Map.of("type", "integer", "description", "要删除的事件ID")
                                ),
                                "required", List.of("eventId")
                        )
                ),
                Map.of(
                        "name", "get_event",
                        "description", "查看单个事件详情。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "eventId", Map.of("type", "integer", "description", "事件ID")
                                ),
                                "required", List.of("eventId")
                        )
                )
        );
    }

    // ── 工具执行 ──

    @SuppressWarnings("unchecked")
    private String executeToolCall(Long userId, String toolName, String arguments, Consumer<String> sseSender) {
        try {
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
            return switch (toolName) {
                case "create_event" -> {
                    CreateEventRequest req = new CreateEventRequest();
                    req.setTitle((String) args.get("title"));
                    req.setDescription((String) args.get("description"));
                    req.setStartTime(parseDateTime((String) args.get("startTime")));
                    req.setEndTime(parseDateTime((String) args.get("endTime")));
                    req.setLocation((String) args.get("location"));
                    req.setParticipants((List<String>) args.get("participants"));
                    req.setTags((List<String>) args.get("tags"));
                    EventVO ev = eventService.createEvent(userId, req);
                    sendEventData(sseSender, "create", List.of(ev));
                    yield "事件已创建: " + ev.getTitle() + " (ID: " + ev.getId() + ")";
                }
                case "list_events" -> {
                    String startDate = (String) args.get("startDate");
                    String endDate = (String) args.get("endDate");
                    Integer status = args.get("status") != null ? ((Number) args.get("status")).intValue() : null;
                    var page = eventService.listEvents(userId, startDate, endDate, status, null, null, 1, 20);
                    if (page.getRecords().isEmpty()) {
                        yield "该时间范围内没有事件。";
                    }
                    var sb = new StringBuilder("查询到 " + page.getTotal() + " 个事件:\n");
                    for (var ev : page.getRecords()) {
                        sb.append("- [").append(ev.getId()).append("] ").append(ev.getTitle())
                                .append(" (").append(ev.getStartTime()).append(" ~ ").append(ev.getEndTime()).append(")")
                                .append(ev.getStatus() == 1 ? " ✓已完成" : "").append("\n");
                    }
                    yield sb.toString();
                }
                case "update_event" -> {
                    Long eventId = ((Number) args.get("eventId")).longValue();
                    PatchEventRequest req = new PatchEventRequest();
                    if (args.get("title") != null) req.setTitle((String) args.get("title"));
                    if (args.get("startTime") != null) req.setStartTime(parseDateTime((String) args.get("startTime")));
                    if (args.get("endTime") != null) req.setEndTime(parseDateTime((String) args.get("endTime")));
                    if (args.get("location") != null) req.setLocation((String) args.get("location"));
                    EventVO ev = eventService.patchEvent(userId, eventId, req);
                    sendEventData(sseSender, "update", List.of(ev));
                    yield "事件已更新: " + ev.getTitle();
                }
                case "delete_event" -> {
                    Long eventId = ((Number) args.get("eventId")).longValue();
                    eventService.deleteEvent(userId, eventId);
                    sendEventData(sseSender, "delete", List.of(EventVO.builder().id(eventId).build()));
                    yield "事件已删除 (ID: " + eventId + ")";
                }
                case "get_event" -> {
                    Long eventId = ((Number) args.get("eventId")).longValue();
                    EventVO ev = eventService.getEvent(userId, eventId);
                    yield "事件详情: [" + ev.getId() + "] " + ev.getTitle()
                            + "\n时间: " + ev.getStartTime() + " ~ " + ev.getEndTime()
                            + "\n描述: " + (ev.getDescription() != null ? ev.getDescription() : "无")
                            + "\n状态: " + (ev.getStatus() == 1 ? "已完成" : "未完成");
                }
                default -> "未知操作: " + toolName;
            };
        } catch (Exception e) {
            log.error("[voice] 工具执行失败: {} args={}", toolName, arguments, e);
            return "操作失败: " + e.getMessage();
        }
    }

    // ── 工具辅助方法 ──

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private void sendSse(Consumer<String> sender, String type, Object data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", type);
            if (data instanceof String) {
                payload.put("content", data);
            } else if (data instanceof Map) {
                payload.putAll((Map<String, Object>) data);
            }
            sender.accept(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("[voice] SSE 发送失败", e);
        }
    }

    private void sendEventData(Consumer<String> sender, String action, List<EventVO> events) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "event_data");
            payload.put("action", action);
            payload.put("events", events.stream().map(ev -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", ev.getId());
                m.put("title", ev.getTitle());
                m.put("description", ev.getDescription());
                m.put("startTime", ev.getStartTime() != null ? ev.getStartTime().toString() : null);
                m.put("endTime", ev.getEndTime() != null ? ev.getEndTime().toString() : null);
                m.put("duration", ev.getDuration());
                m.put("location", ev.getLocation());
                m.put("status", ev.getStatus());
                m.put("participants", ev.getParticipants());
                m.put("tags", ev.getTags());
                m.put("reminderBefore", ev.getReminderBefore());
                return m;
            }).toList());
            sender.accept(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("[voice] event_data SSE 发送失败", e);
        }
    }

    private String describeToolCall(String toolName) {
        return switch (toolName) {
            case "create_event" -> "创建日程";
            case "list_events" -> "查询日程";
            case "update_event" -> "修改日程";
            case "delete_event" -> "删除日程";
            case "get_event" -> "查看日程详情";
            default -> "处理中";
        };
    }
}
