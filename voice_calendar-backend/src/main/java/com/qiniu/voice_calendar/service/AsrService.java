package com.qiniu.voice_calendar.service;

public interface AsrService {
    String transcribe(byte[] audioData, String mimeType);
}
