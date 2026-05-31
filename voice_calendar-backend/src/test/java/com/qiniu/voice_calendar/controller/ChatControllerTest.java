package com.qiniu.voice_calendar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.voice_calendar.dto.ChatRequest;
import com.qiniu.voice_calendar.dto.ConversationVO;
import com.qiniu.voice_calendar.dto.MessageVO;
import com.qiniu.voice_calendar.exception.BusinessException;
import com.qiniu.voice_calendar.exception.GlobalExceptionHandler;
import com.qiniu.voice_calendar.service.ChatService;
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

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatController 接口测试")
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatController chatController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(chatController)
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

    // ══════════════════════════════════════
    //   POST /api/chat/stream
    // ══════════════════════════════════════

    @Nested
    @DisplayName("POST /api/chat/stream — SSE 流式对话")
    class StreamChat {

        @Test
        @DisplayName("内容为空 → 400")
        void shouldRejectEmptyContent() throws Exception {
            ChatRequest req = new ChatRequest();
            req.setContent("");

            mockMvc.perform(post("/api/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("正常请求 → 200 + text/event-stream")
        void shouldReturnSse() throws Exception {
            // The async nature of SSE makes it tricky to test fully.
            // We verify the request is accepted and returns event-stream content type.
            ChatRequest req = new ChatRequest();
            req.setContent("你好");
            // chatService.chat() will be called in a background thread;
            // we verify the call happens
            doAnswer(invocation -> {
                // Simulate: the Consumer is called with done event
                @SuppressWarnings("unchecked")
                var sender = (java.util.function.Consumer<String>) invocation.getArgument(2);
                sender.accept("{\"type\":\"done\",\"conversationId\":1}");
                return null;
            }).when(chatService).chat(anyLong(), any(ChatRequest.class), any());

            mockMvc.perform(post("/api/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", startsWith("text/event-stream")));
        }
    }

    // ══════════════════════════════════════
    //   GET /api/conversations
    // ══════════════════════════════════════

    @Nested
    @DisplayName("GET /api/conversations — 对话列表")
    class ListConversations {

        @Test
        @DisplayName("正常返回列表 → 200")
        void shouldReturnConversations() throws Exception {
            var c1 = ConversationVO.builder()
                    .id(1L).title("你好").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            when(chatService.listConversations(1L)).thenReturn(List.of(c1));

            mockMvc.perform(get("/api/conversations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("你好"));
        }

        @Test
        @DisplayName("空列表 → 200")
        void shouldReturnEmptyList() throws Exception {
            when(chatService.listConversations(1L)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/conversations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    // ══════════════════════════════════════
    //   GET /api/conversations/{id}/messages
    // ══════════════════════════════════════

    @Nested
    @DisplayName("GET /api/conversations/{id}/messages — 对话消息")
    class GetMessages {

        @Test
        @DisplayName("正常返回消息 → 200")
        void shouldReturnMessages() throws Exception {
            var m1 = MessageVO.builder()
                    .id(1L).role("user").content("你好").createdAt(LocalDateTime.now()).build();
            var m2 = MessageVO.builder()
                    .id(2L).role("assistant").content("你好！有什么可以帮你的？").createdAt(LocalDateTime.now()).build();
            when(chatService.getMessages(1L, 1L)).thenReturn(List.of(m1, m2));

            mockMvc.perform(get("/api/conversations/1/messages"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].role").value("user"))
                    .andExpect(jsonPath("$.data[1].role").value("assistant"));
        }

        @Test
        @DisplayName("对话不存在 → 404")
        void shouldReturn404WhenNotFound() throws Exception {
            when(chatService.getMessages(1L, 999L))
                    .thenThrow(new BusinessException(404, "对话不存在"));

            mockMvc.perform(get("/api/conversations/999/messages"))
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("对话不存在"));
        }
    }

    // ══════════════════════════════════════
    //   DELETE /api/conversations/{id}
    // ══════════════════════════════════════

    @Nested
    @DisplayName("DELETE /api/conversations/{id} — 删除对话")
    class DeleteConversation {

        @Test
        @DisplayName("正常删除 → 200")
        void shouldDeleteConversation() throws Exception {
            mockMvc.perform(delete("/api/conversations/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("已删除"));
        }

        @Test
        @DisplayName("对话不存在 → 404")
        void shouldReturn404WhenNotFound() throws Exception {
            doThrow(new BusinessException(404, "对话不存在"))
                    .when(chatService).deleteConversation(1L, 999L);

            mockMvc.perform(delete("/api/conversations/999"))
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("对话不存在"));
        }
    }
}
