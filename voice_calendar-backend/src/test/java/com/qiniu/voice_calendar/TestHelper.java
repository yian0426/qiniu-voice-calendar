package com.qiniu.voice_calendar;

import com.qiniu.voice_calendar.config.JwtProperties;
import com.qiniu.voice_calendar.util.JwtUtil;
import org.springframework.stereotype.Component;

/**
 * Shared test utility. Provides a pre-built JWT token for authenticated test requests.
 * Injects the real JwtUtil backed by test application.yaml config.
 */
@Component
public class TestHelper {

    public final JwtUtil jwtUtil;

    /** Token for user id=1, username=testuser */
    public final String validToken;

    public TestHelper(JwtProperties jwtProperties) {
        // Re-create JwtUtil to ensure it uses test config values
        this.jwtUtil = new JwtUtil(jwtProperties);
        this.validToken = jwtUtil.generateToken(1L, "testuser");
    }

    public String tokenFor(Long userId, String username) {
        return jwtUtil.generateToken(userId, username);
    }
}
