package com.qiniu.voice_calendar.service;

import java.util.function.Consumer;

/**
 * 语音处理服务 — 接收前端语音转录文本，通过 MiMo AI 解析为日程操作。
 * 无状态：不保存对话历史，每次请求独立处理。
 */
public interface VoiceService {

    /**
     * 处理语音转录文本，通过 SSE 返回结果。
     *
     * @param userId    当前用户 ID
     * @param text      语音转录的文本
     * @param sseSender SSE 事件发送器
     */
    void processVoice(Long userId, String text, Consumer<String> sseSender);
}
