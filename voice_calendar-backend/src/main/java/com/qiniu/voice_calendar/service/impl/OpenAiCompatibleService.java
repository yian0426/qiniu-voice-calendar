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
import java.util.concurrent.atomic.AtomicInteger;
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
        String format = aiProperties.getFormat();
        if ("anthropic".equalsIgnoreCase(format)) {
            streamChatAnthropic(messages, tools, callback);
        } else {
            streamChatOpenAi(messages, tools, callback);
        }
    }

    // ══════════════════════════════════════════════════
    //  OpenAI Compatible Format
    // ══════════════════════════════════════════════════

    private void streamChatOpenAi(List<ChatMessage> messages, List<Map<String, Object>> tools,
                                  Consumer<StreamEvent> callback) {
        try {
            var bodyNode = objectMapper.createObjectNode();
            bodyNode.put("model", aiProperties.getModel());
            bodyNode.put("stream", true);
            bodyNode.set("messages", buildOpenAiMessagesArray(messages));

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
            log.debug("OpenAI request: {}", body);

            // Build auth header based on configuration
            String authHeaderName;
            String authHeaderValue;
            if ("bearer".equalsIgnoreCase(aiProperties.getAuthStyle())) {
                authHeaderName = "Authorization";
                authHeaderValue = "Bearer " + aiProperties.getApiKey();
            } else {
                authHeaderName = "api-key";
                authHeaderValue = aiProperties.getApiKey();
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiProperties.getBaseUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header(authHeaderName, authHeaderValue)
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

            parseOpenAiStream(response.body(), callback);
        } catch (Exception e) {
            log.error("AI API call failed", e);
            callback.accept(StreamEvent.error("AI 服务调用失败: " + e.getMessage()));
        }
    }

    private void parseOpenAiStream(java.io.InputStream inputStream, Consumer<StreamEvent> callback)
            throws Exception {
        int lineCount = 0;
        int contentChunks = 0;
        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            var toolCalls = new ArrayList<ToolCallAccum>();

            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (line.isBlank()) continue;
                if (!line.startsWith("data: ")) {
                    log.trace("[ai-stream] 跳过非数据行: {}", line);
                    continue;
                }
                String data = line.substring(6);
                if ("[DONE]".equals(data.trim())) {
                    log.debug("[ai-stream] 收到 [DONE] 标记, 共处理 {} 行, {} 个内容块", lineCount, contentChunks);
                    break;
                }

                JsonNode node;
                try {
                    node = objectMapper.readTree(data);
                } catch (Exception e) {
                    log.warn("[ai-stream] JSON 解析失败: {}", data.length() > 100 ? data.substring(0, 100) + "..." : data);
                    continue;
                }

                JsonNode choices = node.get("choices");
                if (choices == null || choices.isEmpty()) continue;
                JsonNode delta = choices.get(0).get("delta");
                if (delta == null) continue;

                JsonNode contentNode = delta.get("content");
                if (contentNode != null && !contentNode.isNull()) {
                    String text = contentNode.asText();
                    if (!text.isEmpty()) {
                        contentChunks++;
                        callback.accept(StreamEvent.content(text));
                    }
                }

                JsonNode toolCallsNode = delta.get("tool_calls");
                if (toolCallsNode != null) {
                    for (JsonNode tc : toolCallsNode) {
                        int idx = tc.get("index").asInt(0);
                        while (toolCalls.size() <= idx) toolCalls.add(new ToolCallAccum());
                        ToolCallAccum accum = toolCalls.get(idx);
                        if (tc.has("id") && !tc.get("id").isNull()) accum.id = tc.get("id").asText();
                        JsonNode fn = tc.get("function");
                        if (fn != null) {
                            if (fn.has("name") && !fn.get("name").isNull()) accum.name = fn.get("name").asText();
                            if (fn.has("arguments") && !fn.get("arguments").isNull()) accum.args.append(fn.get("arguments").asText());
                        }
                    }
                }

                JsonNode finishReason = choices.get(0).get("finish_reason");
                if (finishReason != null && !finishReason.isNull()) {
                    String reason = finishReason.asText();
                    log.debug("[ai-stream] finish_reason={}", reason);
                    if ("tool_calls".equals(reason)) {
                        for (ToolCallAccum accum : toolCalls) {
                            if (accum.id != null) {
                                log.info("[ai-stream] 工具调用完成: {} id={}", accum.name, accum.id);
                                callback.accept(StreamEvent.toolCall(accum.id, accum.name, accum.args.toString()));
                            }
                        }
                    } else if ("stop".equals(reason)) {
                        log.debug("[ai-stream] 正常结束 (stop)");
                    }
                }
            }
        }
        log.debug("[ai-stream] 流解析结束, 共 {} 行, {} 内容块, 发送 DONE", lineCount, contentChunks);
        callback.accept(StreamEvent.done());
    }

    private com.fasterxml.jackson.databind.node.ArrayNode buildOpenAiMessagesArray(List<ChatMessage> messages) {
        var arr = objectMapper.createArrayNode();
        for (var msg : messages) {
            var msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.role());

            if (msg.toolCallId() != null) {
                msgNode.put("tool_call_id", msg.toolCallId());
                msgNode.put("content", msg.content());
            } else if (msg.content() != null) {
                msgNode.put("content", msg.content());
            }

            if (msg.toolName() != null) {
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

    // ══════════════════════════════════════════════════
    //  Anthropic Compatible Format
    // ══════════════════════════════════════════════════

    private void streamChatAnthropic(List<ChatMessage> messages, List<Map<String, Object>> tools,
                                     Consumer<StreamEvent> callback) {
        try {
            var bodyNode = objectMapper.createObjectNode();
            bodyNode.put("model", aiProperties.getModel());
            bodyNode.put("max_tokens", 4096);
            bodyNode.put("stream", true);

            // Extract system prompt
            StringBuilder systemPrompt = new StringBuilder();
            var messagesArray = objectMapper.createArrayNode();
            for (var msg : messages) {
                if ("system".equals(msg.role())) {
                    if (systemPrompt.length() > 0) systemPrompt.append("\n");
                    systemPrompt.append(msg.content());
                } else if ("user".equals(msg.role()) || "assistant".equals(msg.role())) {
                    var msgNode = objectMapper.createObjectNode();
                    msgNode.put("role", msg.role());
                    msgNode.put("content", msg.content() != null ? msg.content() : "");
                    messagesArray.add(msgNode);
                } else if ("tool".equals(msg.role())) {
                    var msgNode = objectMapper.createObjectNode();
                    msgNode.put("role", "user");
                    var contentArr = objectMapper.createArrayNode();
                    var toolResult = objectMapper.createObjectNode();
                    toolResult.put("type", "tool_result");
                    toolResult.put("tool_use_id", msg.toolCallId());
                    toolResult.put("content", msg.content() != null ? msg.content() : "");
                    contentArr.add(toolResult);
                    msgNode.set("content", contentArr);
                    messagesArray.add(msgNode);
                }
            }

            if (systemPrompt.length() > 0) {
                bodyNode.put("system", systemPrompt.toString());
            }
            bodyNode.set("messages", messagesArray);

            if (tools != null && !tools.isEmpty()) {
                var toolsArray = objectMapper.createArrayNode();
                for (var tool : tools) {
                    var toolNode = objectMapper.createObjectNode();
                    toolNode.put("name", (String) tool.get("name"));
                    toolNode.put("description", (String) tool.get("description"));
                    toolNode.set("input_schema", objectMapper.valueToTree(tool.get("parameters")));
                    toolsArray.add(toolNode);
                }
                bodyNode.set("tools", toolsArray);
            }

            String body = objectMapper.writeValueAsString(bodyNode);
            log.debug("Anthropic request: {}", body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiProperties.getBaseUrl() + "/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", aiProperties.getApiKey())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("Anthropic API error ({}): {}", response.statusCode(), errorBody);
                callback.accept(StreamEvent.error("AI 服务返回错误: " + response.statusCode()));
                return;
            }

            parseAnthropicStream(response.body(), callback);
        } catch (Exception e) {
            log.error("Anthropic API call failed", e);
            callback.accept(StreamEvent.error("AI 服务调用失败: " + e.getMessage()));
        }
    }

    private void parseAnthropicStream(java.io.InputStream inputStream, Consumer<StreamEvent> callback)
            throws Exception {
        int lineCount = 0;
        int contentChunks = 0;
        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            String eventType = null;
            var toolId = new StringBuilder();
            var toolName = new StringBuilder();
            var toolInputJson = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (line.startsWith("event: ")) {
                    eventType = line.substring(7).trim();
                } else if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        log.debug("[ai-stream-anthropic] 收到 [DONE], 共 {} 行, {} 内容块", lineCount, contentChunks);
                        break;
                    }
                    if (eventType == null) continue;

                    JsonNode node;
                    try { node = objectMapper.readTree(data); }
                    catch (Exception e) { continue; }

                    switch (eventType) {
                        case "content_block_start" -> {
                            JsonNode contentBlock = node.get("content_block");
                            if (contentBlock != null) {
                                String type = contentBlock.has("type") ? contentBlock.get("type").asText() : "";
                                if ("tool_use".equals(type)) {
                                    toolId.setLength(0);
                                    toolName.setLength(0);
                                    toolInputJson.setLength(0);
                                    if (contentBlock.has("id")) toolId.append(contentBlock.get("id").asText());
                                    if (contentBlock.has("name")) toolName.append(contentBlock.get("name").asText());
                                    log.info("[ai-stream-anthropic] 工具调用开始: {}", toolName);
                                }
                            }
                        }
                        case "content_block_delta" -> {
                            JsonNode delta = node.get("delta");
                            if (delta != null) {
                                String type = delta.has("type") ? delta.get("type").asText() : "";
                                if ("text_delta".equals(type) && delta.has("text")) {
                                    String text = delta.get("text").asText();
                                    if (!text.isEmpty()) {
                                        contentChunks++;
                                        callback.accept(StreamEvent.content(text));
                                    }
                                } else if ("input_json_delta".equals(type) && delta.has("partial_json")) {
                                    toolInputJson.append(delta.get("partial_json").asText());
                                }
                            }
                        }
                        case "content_block_stop" -> {
                            if (toolId.length() > 0 && toolName.length() > 0) {
                                log.info("[ai-stream-anthropic] 工具调用完成: {} id={}", toolName, toolId);
                                callback.accept(StreamEvent.toolCall(
                                        toolId.toString(), toolName.toString(), toolInputJson.toString()));
                            }
                            toolId.setLength(0);
                            toolName.setLength(0);
                            toolInputJson.setLength(0);
                        }
                        case "message_stop" -> {
                            log.debug("[ai-stream-anthropic] message_stop 事件");
                        }
                        case "error" -> {
                            String message = node.has("message") ? node.get("message").asText() : "Unknown error";
                            log.warn("[ai-stream-anthropic] 错误: {}", message);
                            callback.accept(StreamEvent.error(message));
                        }
                    }
                    eventType = null;
                }
            }
        }
        log.debug("[ai-stream-anthropic] 流解析结束, 共 {} 行, {} 内容块, 发送 DONE", lineCount, contentChunks);
        callback.accept(StreamEvent.done());
    }

    // ── Helper ──

    private static class ToolCallAccum {
        String id;
        String name;
        StringBuilder args = new StringBuilder();
    }
}
