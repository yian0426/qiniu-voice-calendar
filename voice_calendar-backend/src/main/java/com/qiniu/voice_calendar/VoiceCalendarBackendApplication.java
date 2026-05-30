package com.qiniu.voice_calendar;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.qiniu.voice_calendar.mapper")
public class VoiceCalendarBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoiceCalendarBackendApplication.class, args);
    }

}
