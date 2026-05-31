package com.qiniu.voice_calendar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.voice_calendar.dto.*;
import com.qiniu.voice_calendar.entity.Conversation;
import com.qiniu.voice_calendar.entity.Message;
import com.qiniu.voice_calendar.exception.BusinessException;
import com.qiniu.voice_calendar.mapper.ConversationMapper;
import com.qiniu.voice_calendar.mapper.MessageMapper;
import com.qiniu.voice_calendar.service.AiService;
import com.qiniu.voice_calendar.service.ChatService;
import com.qiniu.voice_calendar.service.EventService;
import com.qiniu.voice_calendar.service.StreamEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final AiService aiService;
    private final EventService eventService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void chat(Long userId, ChatRequest request, Consumer<String> sseSender) {
        // 1. Get or create conversation
        Conversation conv;
        if (request.getConversationId() != null) {
            conv = conversationMapper.selectById(request.getConversationId());
            if (conv == null || !conv.getUserId().equals(userId)) {
                throw new BusinessException(404, "对话不存在");
            }
        } else {
            conv = new Conversation();
            conv.setUserId(userId);
            conv.setTitle(truncate(request.getContent(), 50));
            conv.setCreatedAt(LocalDateTime.now());
            conv.setUpdatedAt(LocalDateTime.now());
            conversationMapper.insert(conv);
        }

        // 2. Save user message
        Message userMsg = new Message();
        userMsg.setConversationId(conv.getId());
        userMsg.setRole("user");
        userMsg.setContent(request.getContent());
        userMsg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(userMsg);

        // 3. Load conversation history and build LLM messages
        List<Message> history = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conv.getId())
                        .orderByAsc(Message::getCreatedAt));
        List<AiService.ChatMessage> llmMessages = buildLlmMessages(history);

        // 4. Collect assistant response and tool calls from streaming
        var assistantContent = new StringBuilder();
        var toolCalls = new ArrayList<StreamEvent>();

        List<Map<String, Object>> tools = buildToolDefinitions();
        aiService.streamChat(llmMessages, tools, event -> {
            switch (event.type()) {
                case CONTENT -> {
                    assistantContent.append(event.content());
                    sendSse(sseSender, "content", event.content());
                }
                case TOOL_CALL -> {
                    toolCalls.add(event);
                    sendSse(sseSender, "status", "正在执行: " + event.toolName());
                }
                case ERROR -> sendSse(sseSender, "error", event.content() != null ? event.content() : "未知错误");
                case DONE -> { /* handled after stream */ }
            }
        });

        // 5. If there were tool calls, execute them and do a second LLM call
        String finalContent;
        if (!toolCalls.isEmpty()) {
            // Add assistant tool_call message to LLM context
            for (var tc : toolCalls) {
                llmMessages.add(new AiService.ChatMessage("assistant", tc.arguments(), tc.toolCallId(), tc.toolName()));
            }
            // Execute tool calls and add results
            for (var tc : toolCalls) {
                String result = executeToolCall(userId, tc.toolName(), tc.arguments());
                llmMessages.add(new AiService.ChatMessage("tool", result, tc.toolCallId(), null));
            }

            // Second LLM call — no tools
            aiService.streamChat(llmMessages, null, event -> {
                switch (event.type()) {
                    case CONTENT -> {
                        assistantContent.append(event.content());
                        sendSse(sseSender, "content", event.content());
                    }
                    case ERROR -> sendSse(sseSender, "error", event.content() != null ? event.content() : "未知错误");
                    case DONE -> { /* ok */ }
                    default -> {}
                }
            });
        }

        finalContent = assistantContent.toString();

        // 6. Save assistant message
        Message assistantMsg = new Message();
        assistantMsg.setConversationId(conv.getId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(finalContent.isEmpty() ? "已处理" : finalContent);
        assistantMsg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(assistantMsg);

        // Update conversation timestamp
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);

        // 7. Signal completion
        sendSse(sseSender, "done", Map.of("conversationId", conv.getId(), "title", conv.getTitle()));
    }

    @Override
    public List<ConversationVO> listConversations(Long userId) {
        List<Conversation> list = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .orderByDesc(Conversation::getUpdatedAt));
        return list.stream()
                .map(c -> ConversationVO.builder()
                        .id(c.getId()).title(c.getTitle())
                        .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageVO> getMessages(Long userId, Long conversationId) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new BusinessException(404, "对话不存在");
        }
        List<Message> list = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreatedAt));
        return list.stream()
                .map(m -> MessageVO.builder()
                        .id(m.getId()).role(m.getRole()).content(m.getContent())
                        .createdAt(m.getCreatedAt()).build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new BusinessException(404, "对话不存在");
        }
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }

    // ── Private helpers ──

    private List<AiService.ChatMessage> buildLlmMessages(List<Message> history) {
        // Add system prompt as first message
        List<AiService.ChatMessage> result = new ArrayList<>();
        result.add(new AiService.ChatMessage("system",
                "你是一个智能日历助手。你可以帮助用户管理日程，包括创建、查询、修改和删除事件。请用中文回复。当用户提到时间相关操作时，请结合当前时间进行理解。当前时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"))));
        for (var msg : history) {
            result.add(new AiService.ChatMessage(msg.getRole(), msg.getContent()));
        }
        return result;
    }

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
                        "description", "修改已有的日历事件。当用户说'修改'、'改一下'、'推迟'、'提前'、'换个时间'时使用。需要提供事件ID。",
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
                        "description", "查看单个事件详情。当用户说'看一下'、'详情'某个具体事件时使用。",
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

    @SuppressWarnings("unchecked")
    private String executeToolCall(Long userId, String toolName, String arguments) {
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
                    yield "事件已创建: " + ev.getTitle() + " (ID: " + ev.getId() + ")，时间: " + ev.getStartTime() + " ~ " + ev.getEndTime();
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
                    // Use patch for partial update
                    PatchEventRequest req = new PatchEventRequest();
                    if (args.get("title") != null) req.setTitle((String) args.get("title"));
                    if (args.get("startTime") != null) req.setStartTime(parseDateTime((String) args.get("startTime")));
                    if (args.get("endTime") != null) req.setEndTime(parseDateTime((String) args.get("endTime")));
                    if (args.get("location") != null) req.setLocation((String) args.get("location"));
                    EventVO ev = eventService.patchEvent(userId, eventId, req);
                    yield "事件已更新: " + ev.getTitle();
                }
                case "delete_event" -> {
                    Long eventId = ((Number) args.get("eventId")).longValue();
                    eventService.deleteEvent(userId, eventId);
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
            log.error("Tool execution failed: {} args={}", toolName, arguments, e);
            return "操作失败: " + e.getMessage();
        }
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            // Try date-only format
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "新对话";
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
            log.error("Failed to send SSE event", e);
        }
    }
}
