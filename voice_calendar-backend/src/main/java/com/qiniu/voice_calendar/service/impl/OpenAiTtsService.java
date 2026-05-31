package com.qiniu.voice_calendar.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.voice_calendar.config.AiProperties;
import com.qiniu.voice_calendar.exception.BusinessException;
import com.qiniu.voice_calendar.service.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiTtsService implements TtsService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Override
    public byte[] synthesize(String text) {
        try {
            var bodyNode = objectMapper.createObjectNode();
            bodyNode.put("model", aiProperties.getTtsModel());
            bodyNode.put("input", text);
            bodyNode.put("voice", aiProperties.getTtsVoice());

            String bodyStr = objectMapper.writeValueAsString(bodyNode);

            String authHeaderName;
            String authHeaderValue;
            if ("bearer".equalsIgnoreCase(aiProperties.getAuthStyle())) {
                authHeaderName = "Authorization";
                authHeaderValue = "Bearer " + aiProperties.getApiKey();
            } else {
                authHeaderName = "api-key";
                authHeaderValue = aiProperties.getApiKey();
            }

            String baseUrl = audioBaseUrl();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/audio/speech"))
                    .header("Content-Type", "application/json")
                    .header(authHeaderName, authHeaderValue)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8))
                    .build();

            log.debug("TTS request: url={}, model={}, voice={}, textLen={}",
                    baseUrl + "/audio/speech", aiProperties.getTtsModel(), aiProperties.getTtsVoice(), text.length());

            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body(), StandardCharsets.UTF_8);
                log.error("TTS API error ({}): {}", response.statusCode(), errorBody);
                throw new BusinessException(500, "语音合成失败");
            }

            log.info("TTS success, audio size={}bytes", response.body().length);
            return response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("TTS request failed", e);
            throw new BusinessException(500, "语音合成失败: " + e.getMessage());
        }
    }

    private String audioBaseUrl() {
        String audioUrl = aiProperties.getAudioBaseUrl();
        return (audioUrl != null && !audioUrl.isBlank()) ? audioUrl : aiProperties.getBaseUrl();
    }
}
