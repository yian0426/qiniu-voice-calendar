package com.qiniu.voice_calendar.service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface AiService {

    /**
     * Stream a chat completion from the LLM.
     * @param messages chat messages (role + content)
     * @param tools list of tool definitions, or null/empty for no tools
     * @param callback receives stream events (CONTENT, TOOL_CALL, DONE, ERROR)
     */
    void streamChat(List<ChatMessage> messages, List<Map<String, Object>> tools,
                    Consumer<StreamEvent> callback);

    /** A single chat message for the LLM context. */
    record ChatMessage(String role, String content, String toolCallId, String toolName) {
        public ChatMessage(String role, String content) {
            this(role, content, null, null);
        }
    }
}
