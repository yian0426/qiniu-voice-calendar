package com.qiniu.voice_calendar.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.voice_calendar.config.AiProperties;
import com.qiniu.voice_calendar.exception.BusinessException;
import com.qiniu.voice_calendar.service.AsrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiProperties.getBaseUrl() + "/audio/transcriptions"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header(authHeaderName, authHeaderValue)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.error("ASR API error ({}): {}", response.statusCode(), response.body());
                throw new BusinessException(500, "语音识别失败");
            }

            JsonNode json = objectMapper.readTree(response.body());
            String text = json.path("text").asText();
            if (text == null || text.isBlank()) {
                throw new BusinessException(500, "语音识别结果为空，请重试");
            }
            return text.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ASR request failed", e);
            throw new BusinessException(500, "语音识别失败: " + e.getMessage());
        }
    }

    private byte[] buildMultipartBody(byte[] audioData, String mimeType, String boundary) {
        List<byte[]> parts = new ArrayList<>();

        // file part
        String ext = "webm".equals(mimeType) || mimeType.contains("webm") ? "webm"
                : mimeType.contains("wav") ? "wav"
                : mimeType.contains("mp3") || mimeType.contains("mpeg") ? "mp3"
                : "webm";
        parts.add(("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"recording." + ext + "\"\r\n" +
                "Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        parts.add(audioData);
        parts.add("\r\n".getBytes(StandardCharsets.UTF_8));

        // model part
        String model = aiProperties.getModel();
        String asrModel = model != null && !model.isBlank() ? model : "whisper-1";
        parts.add(("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"model\"\r\n\r\n" +
                asrModel + "\r\n").getBytes(StandardCharsets.UTF_8));

        // end
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
