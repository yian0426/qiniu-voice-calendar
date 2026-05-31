package com.qiniu.voice_calendar.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.voice_calendar.config.AiProperties;
import com.qiniu.voice_calendar.exception.BusinessException;
import com.qiniu.voice_calendar.service.AsrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiAsrService implements AsrService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Override
    public String transcribe(byte[] audioData, String mimeType) {
        try {
            String boundary = "Boundary-" + UUID.randomUUID();
            byte[] body = buildMultipartBody(audioData, mimeType, boundary);

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
                    .uri(URI.create(baseUrl + "/audio/transcriptions"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header(authHeaderName, authHeaderValue)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            log.debug("ASR request: url={}, model={}, mime={}, size={}",
                    baseUrl + "/audio/transcriptions", aiProperties.getAsrModel(), mimeType, audioData.length);

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.error("ASR API error ({}): {}", response.statusCode(), response.body());
                throw new BusinessException(500, "语音识别失败，请重试或使用文字输入");
            }

            JsonNode json = objectMapper.readTree(response.body());
            String text = json.path("text").asText();
            if (text == null || text.isBlank()) {
                throw new BusinessException(500, "语音识别结果为空，请重试");
            }
            log.info("ASR result: {}", text);
            return text.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ASR request failed", e);
            throw new BusinessException(500, "语音识别失败: " + e.getMessage());
        }
    }

    private String audioBaseUrl() {
        String audioUrl = aiProperties.getAudioBaseUrl();
        return (audioUrl != null && !audioUrl.isBlank()) ? audioUrl : aiProperties.getBaseUrl();
    }

    private byte[] buildMultipartBody(byte[] audioData, String mimeType, String boundary) {
        List<byte[]> parts = new ArrayList<>();

        String ext = mimeType.contains("webm") ? "webm"
                : mimeType.contains("wav") ? "wav"
                : mimeType.contains("mpeg") || mimeType.contains("mp3") ? "mp3"
                : "webm";
        parts.add(("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"recording." + ext + "\"\r\n" +
                "Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        parts.add(audioData);
        parts.add("\r\n".getBytes(StandardCharsets.UTF_8));

        parts.add(("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"model\"\r\n\r\n" +
                aiProperties.getAsrModel() + "\r\n").getBytes(StandardCharsets.UTF_8));

        parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        int totalLen = parts.stream().mapToInt(b -> b.length).sum();
        byte[] result = new byte[totalLen];
        int offset = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, result, offset, p.length);
            offset += p.length;
        }
        return result;
    }
}
