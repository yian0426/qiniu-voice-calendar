package com.qiniu.voice_calendar.service;

public interface TtsService {
    byte[] synthesize(String text);
}
