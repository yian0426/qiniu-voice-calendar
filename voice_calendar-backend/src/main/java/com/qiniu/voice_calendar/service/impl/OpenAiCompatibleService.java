package com.qiniu.voice_calendar.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.voice_calendar.config.AiProperties;
import com.qiniu.voice_calendar.service.AiService;
import com.qiniu.voice_calendar.service.StreamEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiCompatibleService implements AiService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Override
    public void streamChat(List<ChatMessage> messages, List<Map<String, Object>> tools,
                           Consumer<StreamEvent> callback) {
        try {
            // Build request body
            var bodyNode = objectMapper.createObjectNode();
            bodyNode.put("model", aiProperties.getModel());
            bodyNode.put("stream", true);
            bodyNode.set("messages", buildMessagesArray(messages));

            if (tools != null && !tools.isEmpty()) {
                var toolsArray = objectMapper.createArrayNode();
                for (var tool : tools) {
                    var toolNode = objectMapper.createObjectNode();
                    toolNode.put("type", "function");
                    toolNode.set("function", objectMapper.valueToTree(tool));
                    toolsArray.add(toolNode);
                }
                bodyNode.set("tools", toolsArray);
                bodyNode.put("tool_choice", "auto");
            }

            String body = objectMapper.writeValueAsString(bodyNode);
            log.debug("AI request: {}", body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiProperties.getBaseUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + aiProperties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("AI API error ({}): {}", response.statusCode(), errorBody);
                callback.accept(StreamEvent.error("AI 服务返回错误: " + response.statusCode()));
                return;
            }

            parseStream(response.body(), callback);
        } catch (Exception e) {
            log.error("AI API call failed", e);
            callback.accept(StreamEvent.error("AI 服务调用失败: " + e.getMessage()));
        }
    }

    // ── SSE stream parsing ──

    private void parseStream(java.io.InputStream inputStream, Consumer<StreamEvent> callback)
            throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            // Tool call accumulation state
            var toolCalls = new ArrayList<ToolCallAccum>();

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6);
                if ("[DONE]".equals(data.trim())) {
                    break;
                }

                JsonNode node = objectMapper.readTree(data);
                JsonNode choices = node.get("choices");
                if (choices == null || choices.isEmpty()) continue;
                JsonNode delta = choices.get(0).get("delta");
                if (delta == null) continue;

                // Content delta
                JsonNode contentNode = delta.get("content");
                if (contentNode != null && !contentNode.isNull()) {
                    String text = contentNode.asText();
                    if (!text.isEmpty()) {
                        callback.accept(StreamEvent.content(text));
                    }
                }

                // Tool call delta — accumulate across chunks
                JsonNode toolCallsNode = delta.get("tool_calls");
                if (toolCallsNode != null) {
                    for (JsonNode tc : toolCallsNode) {
                        int idx = tc.get("index").asInt(0);
                        // Grow list as needed
                        while (toolCalls.size() <= idx) {
                            toolCalls.add(new ToolCallAccum());
                        }
                        ToolCallAccum accum = toolCalls.get(idx);
                        if (tc.has("id") && !tc.get("id").isNull()) {
                            accum.id = tc.get("id").asText();
                        }
                        JsonNode fn = tc.get("function");
                        if (fn != null) {
                            if (fn.has("name") && !fn.get("name").isNull()) {
                                accum.name = fn.get("name").asText();
                            }
                            if (fn.has("arguments") && !fn.get("arguments").isNull()) {
                                accum.args.append(fn.get("arguments").asText());
                            }
                        }
                    }
                }

                // Check finish reason
                JsonNode finishReason = choices.get(0).get("finish_reason");
                if (finishReason != null && !finishReason.isNull()) {
                    if ("tool_calls".equals(finishReason.asText())) {
                        for (ToolCallAccum accum : toolCalls) {
                            if (accum.id != null) {
                                callback.accept(StreamEvent.toolCall(accum.id, accum.name, accum.args.toString()));
                            }
                        }
                    }
                }
            }
        }
        callback.accept(StreamEvent.done());
    }

    // ── Messages array builder ──

    private com.fasterxml.jackson.databind.node.ArrayNode buildMessagesArray(List<ChatMessage> messages) {
        var arr = objectMapper.createArrayNode();
        for (var msg : messages) {
            var msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.role());

            if (msg.toolCallId() != null) {
                // Tool result message
                msgNode.put("tool_call_id", msg.toolCallId());
                msgNode.put("content", msg.content());
            } else if (msg.content() != null) {
                msgNode.put("content", msg.content());
            }

            if (msg.toolName() != null) {
                // Assistant message with tool call
                var tc = objectMapper.createObjectNode();
                tc.put("id", msg.toolCallId());
                tc.put("type", "function");
                var fn = objectMapper.createObjectNode();
                fn.put("name", msg.toolName());
                fn.put("arguments", msg.content());
                tc.set("function", fn);
                var tcArr = objectMapper.createArrayNode();
                tcArr.add(tc);
                msgNode.set("tool_calls", tcArr);
            }

            arr.add(msgNode);
        }
        return arr;
    }

    // ── Helper ──

    private static class ToolCallAccum {
        String id;
        String name;
        StringBuilder args = new StringBuilder();
    }
}
