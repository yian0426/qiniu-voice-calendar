package com.qiniu.voice_calendar.controller;

import com.qiniu.voice_calendar.common.Result;
import com.qiniu.voice_calendar.dto.ChatRequest;
import com.qiniu.voice_calendar.dto.ConversationVO;
import com.qiniu.voice_calendar.dto.MessageVO;
import com.qiniu.voice_calendar.service.ChatService;
import com.qiniu.voice_calendar.util.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ExecutorService executor = Executors.newCachedThreadPool(
            Thread.ofVirtual().name("chat-", 0).factory());

    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody ChatRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        executor.execute(() -> {
            try {
                chatService.chat(userId, request, data -> {
                    try {
                        emitter.send(SseEmitter.event().name("message").data(data));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE send failed", e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("Chat stream error", e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("{\"type\":\"error\",\"content\":\"" + e.getMessage() + "\"}"));
                } catch (IOException ex) {
                    log.error("Failed to send error event", ex);
                }
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(() -> log.debug("SSE timeout for user {}", userId));
        emitter.onError(ex -> log.error("SSE error for user {}", userId, ex));

        return emitter;
    }

    @GetMapping("/api/conversations")
    public Result<List<ConversationVO>> listConversations() {
        Long userId = SecurityContextUtil.getCurrentUserId();
        return Result.ok(chatService.listConversations(userId));
    }

    @GetMapping("/api/conversations/{id}/messages")
    public Result<List<MessageVO>> getMessages(@PathVariable Long id) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        return Result.ok(chatService.getMessages(userId, id));
    }

    @DeleteMapping("/api/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        chatService.deleteConversation(userId, id);
        return Result.ok("已删除");
    }
}
