package com.qiniu.voice_calendar.controller;

import com.qiniu.voice_calendar.dto.VoiceRequest;
import com.qiniu.voice_calendar.service.VoiceService;
import com.qiniu.voice_calendar.util.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 语音处理控制器 — 接收前端语音转录文本，通过 MiMo AI 解析为日程操作。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceService voiceService;
    private final ExecutorService executor = Executors.newCachedThreadPool(
            Thread.ofVirtual().name("voice-", 0).factory());

    /**
     * 语音处理端点 — 接收已转录的文本，返回 SSE 流式结果。
     * 前端使用浏览器 Web Speech API 进行语音识别，将文本发送到此端点。
     */
    @PostMapping(value = "/api/voice/process", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter processVoice(@Valid @RequestBody VoiceRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout

        log.info("[voice] 收到语音处理请求, userId={}, text={}", userId,
                request.getText().length() > 50
                        ? request.getText().substring(0, 50) + "..."
                        : request.getText());

        executor.execute(() -> {
            try {
                voiceService.processVoice(userId, request.getText(), data -> {
                    try {
                        emitter.send(SseEmitter.event().name("message").data(data));
                    } catch (IOException e) {
                        log.warn("[voice] SSE 发送失败 (客户端可能已断开): {}", e.getMessage());
                        throw new RuntimeException("SSE send failed", e);
                    }
                });
                log.debug("[voice] 语音处理完成, userId={}", userId);
                emitter.complete();
            } catch (Exception e) {
                log.error("[voice] 语音处理异常, userId={}", userId, e);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data("{\"type\":\"error\",\"content\":\"" + escapeJson(e.getMessage()) + "\"}"));
                    emitter.send(SseEmitter.event().name("message")
                            .data("{\"type\":\"done\"}"));
                } catch (IOException ex) {
                    log.debug("[voice] 发送错误事件失败: {}", ex.getMessage());
                }
                emitter.complete();
            }
        });

        emitter.onTimeout(() -> {
            log.warn("[voice] SSE 超时, userId={}", userId);
            try {
                emitter.send(SseEmitter.event().name("message")
                        .data("{\"type\":\"error\",\"content\":\"语音处理超时\"}"));
                emitter.send(SseEmitter.event().name("message")
                        .data("{\"type\":\"done\"}"));
            } catch (IOException e) { /* ignore */ }
            emitter.complete();
        });
        emitter.onError(ex -> log.error("[voice] SSE 错误, userId={}", userId, ex));

        return emitter;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
