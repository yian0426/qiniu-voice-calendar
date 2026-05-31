package com.qiniu.voice_calendar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.voice_calendar.common.Result;
import com.qiniu.voice_calendar.dto.ChatRequest;
import com.qiniu.voice_calendar.dto.ConversationVO;
import com.qiniu.voice_calendar.dto.MessageVO;
import com.qiniu.voice_calendar.entity.Message;
import com.qiniu.voice_calendar.mapper.MessageMapper;
import com.qiniu.voice_calendar.service.AsrService;
import com.qiniu.voice_calendar.service.ChatService;
import com.qiniu.voice_calendar.service.TtsService;
import com.qiniu.voice_calendar.util.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final AsrService asrService;
    @Autowired(required = false)
    private TtsService ttsService;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;
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
                        log.warn("SSE send failed (client may have disconnected): {}", e.getMessage());
                        throw new RuntimeException("SSE send failed", e);
                    }
                });
                log.debug("Chat stream completed normally for user {}", userId);
                emitter.complete();
            } catch (Exception e) {
                log.error("Chat stream error for user {}", userId, e);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data("{\"type\":\"error\",\"content\":\"" + escapeJson(e.getMessage()) + "\"}"));
                    emitter.send(SseEmitter.event().name("message")
                            .data("{\"type\":\"done\"}"));
                } catch (IOException ex) {
                    log.debug("Failed to send error/done event: {}", ex.getMessage());
                }
                emitter.complete();
            }
        });

        emitter.onTimeout(() -> {
            log.warn("SSE timeout for user {}", userId);
            try {
                emitter.send(SseEmitter.event().name("message")
                        .data("{\"type\":\"error\",\"content\":\"请求超时\"}"));
                emitter.send(SseEmitter.event().name("message")
                        .data("{\"type\":\"done\"}"));
            } catch (IOException e) { /* ignore */ }
            emitter.complete();
        });
        emitter.onError(ex -> log.error("SSE error for user {}", userId, ex));

        return emitter;
    }

    @PostMapping(value = "/api/chat/voice", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter voiceChat(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "conversationId", required = false) Long conversationId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(300_000L);

        executor.execute(() -> {
            String userAudioUrl = null;
            String assistantTtsUrl = null;
            try {
                // 1. Save user audio file
                byte[] audioBytes = audio.getBytes();
                String mimeType = audio.getContentType() != null ? audio.getContentType() : "audio/webm";
                userAudioUrl = saveAudioFile(audioBytes, mimeType, userId);

                // 2. ASR — transcribe audio to text
                log.info("[voice] 开始 ASR 识别, userId={}, size={}bytes", userId, audioBytes.length);
                String transcribedText = asrService.transcribe(audioBytes, mimeType);
                log.info("[voice] ASR 识别结果: {}", truncate(transcribedText, 80));

                // 3. Send transcription to frontend
                emitter.send(buildSseEvent("transcription", Map.of("content", transcribedText)));

                // 4. Delegate to existing chat logic
                ChatRequest chatReq = new ChatRequest();
                chatReq.setConversationId(conversationId);
                chatReq.setContent(transcribedText);

                AtomicReference<Long> capturedConvId = new AtomicReference<>();
                StringBuilder assistantContent = new StringBuilder();

                chatService.chat(userId, chatReq, data -> {
                    try {
                        Map<String, Object> map = objectMapper.readValue(data, Map.class);
                        // Intercept done event — send after TTS to keep correct event order
                        if ("done".equals(map.get("type"))) {
                            if (map.get("conversationId") != null) {
                                capturedConvId.set(Long.valueOf(map.get("conversationId").toString()));
                            }
                            // Don't forward yet — will send after TTS
                            return;
                        }
                        emitter.send(SseEmitter.event().name("message").data(data));
                        if ("content".equals(map.get("type")) && map.get("content") != null) {
                            assistantContent.append(map.get("content"));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("SSE send failed", e);
                    }
                });

                // 5. Update user message with recording audio URL
                if (capturedConvId.get() != null && userAudioUrl != null) {
                    updateLatestMessageAudioUrl(capturedConvId.get(), "user", userAudioUrl);
                }

                // 6. TTS — synthesize assistant reply
                String assistantText = assistantContent.toString();
                if (ttsService != null && !assistantText.isBlank()) {
                    log.info("[voice] 开始 TTS 合成, length={}", assistantText.length());
                    byte[] ttsAudio = ttsService.synthesize(assistantText);
                    assistantTtsUrl = saveAudioFile(ttsAudio, "audio/mpeg", userId);

                    updateLatestMessageAudioUrl(capturedConvId.get(), "assistant", assistantTtsUrl);

                    emitter.send(buildSseEvent("audio", Map.of(
                            "url", assistantTtsUrl,
                            "content", truncate(assistantText, 100))));
                    log.info("[voice] TTS 音频已发送: {}", assistantTtsUrl);
                }

                // 7. Done
                emitter.send(buildSseEvent("done", Map.of()));
                emitter.complete();
                log.info("[voice] 语音对话完成, userId={}", userId);
            } catch (Exception e) {
                log.error("[voice] 语音对话异常, userId={}", userId, e);
                try {
                    emitter.send(buildSseEvent("error", Map.of("content", e.getMessage())));
                    emitter.send(buildSseEvent("done", Map.of()));
                } catch (IOException ex) { /* ignore */ }
                emitter.complete();
            }
        });

        emitter.onTimeout(() -> {
            log.warn("[voice] SSE timeout for user {}", userId);
            try {
                emitter.send(buildSseEvent("error", Map.of("content", "请求超时")));
                emitter.send(buildSseEvent("done", Map.of()));
            } catch (IOException e) { /* ignore */ }
            emitter.complete();
        });
        emitter.onError(ex -> log.error("[voice] SSE error for user {}", userId, ex));

        return emitter;
    }

    // ── Helpers ──

    private SseEmitter.SseEventBuilder buildSseEvent(String type, Map<String, Object> data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>(data);
            payload.put("type", type);
            return SseEmitter.event().name("message").data(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to build SSE event", e);
        }
    }

    private String saveAudioFile(byte[] data, String mimeType, Long userId) throws IOException {
        String ext = mimeType.contains("mpeg") || mimeType.contains("mp3") ? "mp3"
                : mimeType.contains("webm") ? "webm"
                : mimeType.contains("wav") ? "wav" : "webm";
        String filename = UUID.randomUUID() + "." + ext;
        Path dir = Paths.get("uploads", "voice");
        Files.createDirectories(dir);
        Path filePath = dir.resolve(filename);
        Files.write(filePath, data);
        return "/uploads/voice/" + filename;
    }

    private void updateLatestMessageAudioUrl(Long convId, String role, String audioUrl) {
        List<Message> list = messageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, convId)
                        .eq(Message::getRole, role)
                        .orderByDesc(Message::getCreatedAt)
                        .last("LIMIT 1"));
        if (!list.isEmpty()) {
            Message msg = list.get(0);
            msg.setAudioUrl(audioUrl);
            messageMapper.updateById(msg);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
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
