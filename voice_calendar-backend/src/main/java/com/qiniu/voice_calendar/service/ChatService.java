package com.qiniu.voice_calendar.service;

import com.qiniu.voice_calendar.dto.ChatRequest;
import com.qiniu.voice_calendar.dto.ConversationVO;
import com.qiniu.voice_calendar.dto.MessageVO;

import java.util.List;
import java.util.function.Consumer;

public interface ChatService {
    void chat(Long userId, ChatRequest request, Consumer<String> sseSender);
    List<ConversationVO> listConversations(Long userId);
    List<MessageVO> getMessages(Long userId, Long conversationId);
    void deleteConversation(Long userId, Long conversationId);
}
